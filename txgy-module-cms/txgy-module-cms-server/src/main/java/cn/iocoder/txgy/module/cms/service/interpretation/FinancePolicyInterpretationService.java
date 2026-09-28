package cn.iocoder.txgy.module.cms.service.interpretation;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.ai.api.model.AiRuntimeModelApi;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeChatReqDTO;
import cn.iocoder.txgy.module.ai.api.model.dto.AiRuntimeChatRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.FINANCE_INTERPRETATION_INVALID;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.FINANCE_INTERPRETATION_NOT_FOUND;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.FINANCE_INTERPRETATION_UNAVAILABLE;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.FINANCE_POLICY_CATEGORY_REQUIRED;

/** 金融政策结构化 AI 解读。所有引用均在后端与本次 RAG 上下文逐条核验。 */
@Service
@Slf4j
public class FinancePolicyInterpretationService {

    public static final String DISCLAIMER = "AI 解读仅供政策信息参考，最终以发布机关原文和实际受理部门意见为准。"
            + "政策存在地区、时间和批次限制，本平台不保证申报成功。";
    private static final int MAX_ATTEMPTS = 3;

    private final JdbcTemplate jdbc;
    private final AiRuntimeModelApi aiApi;

    public FinancePolicyInterpretationService(JdbcTemplate jdbc, AiRuntimeModelApi aiApi) {
        this.jdbc = jdbc;
        this.aiApi = aiApi;
    }

    @Transactional(rollbackFor = Exception.class)
    public InterpretationView generate(String reviewId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Policy policy = loadPolicy(tenantId, reviewId);
        List<SourceChunk> chunks = loadChunks(tenantId, reviewId);
        if (chunks.isEmpty()) throw exception(FINANCE_INTERPRETATION_INVALID);

        Map<String, SourceChunk> allowed = new LinkedHashMap<>();
        chunks.forEach(chunk -> allowed.put(chunk.chunkId(), chunk));
        String prompt = buildPrompt(policy, chunks);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                AiRuntimeChatReqDTO request = new AiRuntimeChatReqDTO();
                request.setSceneCode("FINANCE_INTERPRETATION");
                request.setSystemPrompt(systemPrompt());
                request.setUserPrompt(prompt + (attempt == 1 ? "" : "\n上一次输出未通过 JSON Schema 或引用校验，请完整重新输出合法 JSON。"));
                request.setTemperature(0.1D);
                request.setMaxTokens(4000);
                AiRuntimeChatRespDTO response = aiApi.chat(request).getCheckedData();
                Interpretation result = JsonUtils.parseObject(response.getContent(), Interpretation.class);
                validate(result, allowed);
                String json = JsonUtils.toJsonString(result);
                jdbc.update("""
                        INSERT INTO cms_finance_policy_ext
                          (tenant_id,review_id,interpretation_status,interpretation_json,policy_status,version)
                        VALUES (?,?,'APPROVED',CAST(? AS JSON),1,0)
                        ON DUPLICATE KEY UPDATE interpretation_status='APPROVED',
                          interpretation_json=CAST(? AS JSON),version=version+1
                        """, tenantId, reviewId, json, json);
                return new InterpretationView(reviewId, "APPROVED", result, DISCLAIMER,
                        policy.localPdfPath(), response.getModelId(), response.getModelName());
            } catch (RuntimeException ex) {
                last = ex;
                log.warn("[generate][金融政策解读第 {} 次生成或校验失败 reviewId={}]", attempt, reviewId, ex);
            }
        }
        if (last != null) log.error("[generate][金融政策解读重试耗尽 reviewId={}]", reviewId, last);
        throw exception(last == null ? FINANCE_INTERPRETATION_UNAVAILABLE : FINANCE_INTERPRETATION_INVALID);
    }

    public InterpretationView get(String reviewId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Policy policy = loadPolicy(tenantId, reviewId);
        List<Stored> rows = jdbc.query("""
                SELECT interpretation_status,interpretation_json
                FROM cms_finance_policy_ext
                WHERE tenant_id=? AND review_id=? AND deleted=0
                """, (rs, row) -> new Stored(rs.getString(1), rs.getString(2)), tenantId, reviewId);
        if (rows.isEmpty() || rows.getFirst().json() == null) throw exception(FINANCE_INTERPRETATION_NOT_FOUND);
        Stored stored = rows.getFirst();
        return new InterpretationView(reviewId, stored.status(),
                JsonUtils.parseObject(stored.json(), Interpretation.class), DISCLAIMER,
                policy.localPdfPath(), null, null);
    }

    /** 门户仅可读取已经发布且自动审核通过的金融政策解读。 */
    public InterpretationView getPublished(String reviewId) {
        InterpretationView view = get(reviewId);
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Integer published = jdbc.queryForObject("""
                SELECT COUNT(*) FROM cms_knowledge_document
                WHERE tenant_id=? AND review_id=? AND doc_category='FINANCE_POLICY'
                  AND published=1 AND deleted_at IS NULL
                """, Integer.class, tenantId, reviewId);
        if (!"APPROVED".equals(view.status()) || published == null || published != 1) {
            throw exception(FINANCE_INTERPRETATION_NOT_FOUND);
        }
        return view;
    }

    @Transactional(rollbackFor = Exception.class)
    public InterpretationView review(String reviewId, boolean approved) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        int updated = jdbc.update("""
                UPDATE cms_finance_policy_ext SET interpretation_status=?,version=version+1
                WHERE tenant_id=? AND review_id=? AND deleted=0
                  AND interpretation_json IS NOT NULL AND interpretation_status IN ('REVIEWING','REJECTED')
                """, approved ? "APPROVED" : "REJECTED", tenantId, reviewId);
        if (updated != 1) throw exception(FINANCE_INTERPRETATION_NOT_FOUND);
        return get(reviewId);
    }

    private Policy loadPolicy(long tenantId, String reviewId) {
        List<Policy> rows = jdbc.query("""
                SELECT title,content,source_url,local_pdf_path,doc_category,published
                FROM cms_knowledge_document
                WHERE tenant_id=? AND review_id=? AND deleted_at IS NULL
                """, (rs, row) -> new Policy(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getBoolean(6)), tenantId, reviewId);
        if (rows.isEmpty() || !"FINANCE_POLICY".equalsIgnoreCase(rows.getFirst().category())) {
            throw exception(FINANCE_POLICY_CATEGORY_REQUIRED);
        }
        return rows.getFirst();
    }

    private List<SourceChunk> loadChunks(long tenantId, String reviewId) {
        return jdbc.query("""
                SELECT ordinal,page_number,content
                FROM cms_knowledge_chunk
                WHERE tenant_id=? AND review_id=? AND valid=1
                ORDER BY ordinal LIMIT 30
                """, (rs, row) -> new SourceChunk(reviewId + ":" + rs.getInt(1),
                (Integer) rs.getObject(2), trim(rs.getString(3), 5000)), tenantId, reviewId);
    }

    private static String systemPrompt() {
        return """
                你是企业金融政策解读助手，只能依据用户提供的政策切片作答。
                严禁提供股票、基金、个人理财建议，严禁承诺贷款审批或补贴申报成功。
                只输出单个 JSON 对象，不得输出 Markdown、代码围栏、解释或额外字段。
                JSON 必须包含 summary、applicableRegions、eligibility、supportMethods、amountDescription、
                applicationPeriod、risks、citations。citations 只能使用上下文中给出的 chunkId、pageNumber 和原文引句。
                无法从原文确认的内容用空数组、空字符串或 null 表示，不得推测。
                """;
    }

    private static String buildPrompt(Policy policy, List<SourceChunk> chunks) {
        return JsonUtils.toJsonString(Map.of(
                "title", policy.title(),
                "sourceUrl", policy.sourceUrl() == null ? "" : policy.sourceUrl(),
                "today", LocalDate.now().toString(),
                "requiredSchema", Map.of(
                        "summary", "string",
                        "applicableRegions", List.of("string"),
                        "eligibility", List.of("string"),
                        "supportMethods", List.of("string"),
                        "amountDescription", "string",
                        "applicationPeriod", Map.of("start", "YYYY-MM-DD|null", "end", "YYYY-MM-DD|null"),
                        "risks", List.of("string"),
                        "citations", List.of(Map.of("chunkId", "string", "pageNumber", "integer|null", "quote", "string"))),
                "chunks", chunks));
    }

    private static void validate(Interpretation value, Map<String, SourceChunk> allowed) {
        if (value == null || blank(value.summary()) || value.applicableRegions() == null
                || value.eligibility() == null || value.supportMethods() == null || value.risks() == null
                || value.citations() == null || value.citations().isEmpty() || value.applicationPeriod() == null) {
            throw new IllegalArgumentException("AI JSON 缺少必填字段");
        }
        for (Citation citation : value.citations()) {
            SourceChunk source = allowed.get(citation.chunkId());
            if (source == null) throw new IllegalArgumentException("引用了不存在的切片");
            if (source.pageNumber() != null && !source.pageNumber().equals(citation.pageNumber())) {
                throw new IllegalArgumentException("引用页码与切片不一致");
            }
            if (blank(citation.quote()) || !compact(source.content()).contains(compact(citation.quote()))) {
                throw new IllegalArgumentException("引用原文不属于对应切片");
            }
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String compact(String value) { return value == null ? "" : value.replaceAll("\\s+", ""); }
    private static String trim(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private record Policy(String title, String content, String sourceUrl, String localPdfPath,
                          String category, boolean published) { }
    private record SourceChunk(String chunkId, Integer pageNumber, String content) { }
    private record Stored(String status, String json) { }

    public record ApplicationPeriod(String start, String end) { }
    public record Citation(String chunkId, Integer pageNumber, String quote) { }
    public record Interpretation(String summary, List<String> applicableRegions, List<String> eligibility,
                                 List<String> supportMethods, String amountDescription,
                                 ApplicationPeriod applicationPeriod, List<String> risks,
                                 List<Citation> citations) { }
    public record InterpretationView(String reviewId, String status, Interpretation interpretation,
                                     String disclaimer, String localPdfPath,
                                     Long modelId, String modelName) { }
}
