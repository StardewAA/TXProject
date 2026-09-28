package cn.iocoder.txgy.module.cms.controller.admin.portal;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.search.config.ElasticsearchSearchProperties;
import cn.iocoder.txgy.framework.search.core.ElasticsearchClientProvider;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.cms.api.law.CmsLawApi;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerRespDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSuggestionDTO;
import cn.iocoder.txgy.module.cms.service.interpretation.FinancePolicyInterpretationService;
import cn.iocoder.txgy.module.cms.service.rag.CmsAiChatService;
import cn.iocoder.txgy.module.cms.service.rag.CmsRagService;
import cn.iocoder.txgy.module.cms.service.publication.PublicationUrlParser;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels;
import cn.iocoder.txgy.module.cms.service.review.ReviewParser;
import cn.iocoder.txgy.module.cms.service.review.ReviewService;
import cn.iocoder.txgy.module.cms.service.storage.CmsUrlSnapshotService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** 企业门户 CMS：只开放已发布内容检索和待审发布申请。 */
@Tag(name = "企业门户 - CMS 检索与发布申请")
@RestController
@RequestMapping("/cms/portal")
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class CmsPortalController {

    private final CmsLawApi lawApi;
    private final CmsRagService ragService;
    private final ReviewService reviewService;
    private final ReviewParser reviewParser;
    private final PublicationUrlParser publicationUrlParser;
    private final CmsUrlSnapshotService snapshotService;
    private final ObjectProvider<ElasticsearchClientProvider> elasticsearchProvider;
    private final ElasticsearchSearchProperties searchProperties;
    private final JdbcTemplate jdbcTemplate;
    private final CmsAiChatService aiChatService;
    private final FinancePolicyInterpretationService financePolicyInterpretationService;

    public CmsPortalController(CmsLawApi lawApi, CmsRagService ragService,
                               ReviewService reviewService, ReviewParser reviewParser,
                               PublicationUrlParser publicationUrlParser,
                               CmsUrlSnapshotService snapshotService,
                               ObjectProvider<ElasticsearchClientProvider> elasticsearchProvider,
                               ElasticsearchSearchProperties searchProperties,
                               JdbcTemplate jdbcTemplate,
                               CmsAiChatService aiChatService,
                               FinancePolicyInterpretationService financePolicyInterpretationService) {
        this.lawApi = lawApi;
        this.ragService = ragService;
        this.reviewService = reviewService;
        this.reviewParser = reviewParser;
        this.publicationUrlParser = publicationUrlParser;
        this.snapshotService = snapshotService;
        this.elasticsearchProvider = elasticsearchProvider;
        this.searchProperties = searchProperties;
        this.jdbcTemplate = jdbcTemplate;
        this.aiChatService = aiChatService;
        this.financePolicyInterpretationService = financePolicyInterpretationService;
    }

    /** 门户内容所属公司租户：个人租户（0）没有自己的 CMS 素材时，回退展示公司租户的门户内容 */
    private static final long PORTAL_CONTENT_TENANT_ID = 1L;

    @GetMapping("/home-sections")
    @Operation(summary = "门户首页内容区块：推荐位/产品/新闻/公告/关于我们/技术资料")
    public CommonResult<Map<String, Object>> homeSections() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<String, Object> data = buildHomeSections(tenantId);
        if (isHomeSectionsEmpty(data) && tenantId != PORTAL_CONTENT_TENANT_ID) {
            // 个人登录（个人租户）没有素材数据，回退到公司租户，保证门户内容可见
            data = buildHomeSections(PORTAL_CONTENT_TENANT_ID);
        }
        return success(data);
    }

    private Map<String, Object> buildHomeSections(long tenantId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("banners", jdbcTemplate.query("""
                SELECT name, title, image_url AS url, target_url AS targetUrl, CONCAT('推荐位/', position) AS tags
                FROM cms_recommend_slot WHERE tenant_id=? AND position='HOME_BANNER' AND status=0 AND deleted=0
                ORDER BY sort, id LIMIT 5
                """, assetRow(), tenantId));
        data.put("products", jdbcTemplate.query("""
                SELECT MIN(name) AS name, MIN(url) AS url, tags, COUNT(*) AS imageCount
                FROM cms_asset a WHERE tenant_id=? AND deleted=0 AND asset_type='IMAGE' AND tags LIKE '产品图/%'
                GROUP BY tags ORDER BY MIN(id) LIMIT 12
                """, assetRow(), tenantId));
        data.put("news", jdbcTemplate.query("""
                SELECT MIN(name) AS name, MIN(url) AS url, tags, COUNT(*) AS imageCount FROM cms_asset
                WHERE tenant_id=? AND deleted=0 AND tags LIKE '文章图/公司新闻/%'
                GROUP BY tags ORDER BY MIN(id) DESC LIMIT 8
                """, assetRow(), tenantId));
        data.put("notices", jdbcTemplate.query("""
                SELECT MIN(name) AS name, MIN(url) AS url, tags, COUNT(*) AS imageCount FROM cms_asset
                WHERE tenant_id=? AND deleted=0 AND tags LIKE '文章图/通知公告/%'
                GROUP BY tags ORDER BY MIN(id) DESC LIMIT 6
                """, assetRow(), tenantId));
        data.put("about", jdbcTemplate.query("""
                SELECT name, url, tags FROM cms_asset
                WHERE tenant_id=? AND deleted=0 AND tags LIKE '关于天信/%'
                GROUP BY name, url, tags ORDER BY MIN(id) LIMIT 12
                """, assetRow(), tenantId));
        data.put("techDocs", jdbcTemplate.query("""
                SELECT name, url, tags, size FROM cms_asset
                WHERE tenant_id=? AND deleted=0 AND asset_type='PDF' ORDER BY name LIMIT 12
                """, assetRow(), tenantId));
        return data;
    }

    private static boolean isHomeSectionsEmpty(Map<String, Object> data) {
        return data.values().stream()
                .allMatch(value -> value instanceof java.util.Collection<?> items && items.isEmpty());
    }

    private static org.springframework.jdbc.core.RowMapper<Map<String, Object>> assetRow() {
        return (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", rs.getString("name"));
            item.put("url", rs.getString("url"));
            item.put("tags", rs.getString("tags"));
            item.put("category", categoryOf(rs.getString("tags")));
            item.put("title", stripExtension(rs.getString("name")));
            try {
                item.put("size", rs.getObject("size"));
            } catch (Exception ignored) {
                item.put("size", null);
            }
            try {
                item.put("imageCount", rs.getInt("imageCount"));
            } catch (Exception ignored) {
                item.put("imageCount", 1);
            }
            return item;
        };
    }

    private static String categoryOf(String tags) {
        if (tags == null) {
            return "";
        }
        String[] parts = tags.split("/");
        return parts.length >= 2 ? parts[1] : "";
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @GetMapping("/highlights")
    @Operation(summary = "门户首页：通知公告与政策动态")
    public CommonResult<Map<String, Object>> highlights() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<String, Object> data = buildHighlights(tenantId);
        if (isEmptyHighlights(data) && tenantId != PORTAL_CONTENT_TENANT_ID) {
            data = buildHighlights(PORTAL_CONTENT_TENANT_ID);
        }
        return success(data);
    }

    private Map<String, Object> buildHighlights(long tenantId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notices", jdbcTemplate.query("""
                SELECT id, title, create_time FROM system_notice
                WHERE deleted=0 AND status=0 AND tenant_id=?
                ORDER BY id DESC LIMIT 6
                """, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("title", rs.getString("title"));
            item.put("date", rs.getTimestamp("create_time") == null ? "" :
                    rs.getTimestamp("create_time").toLocalDateTime().toLocalDate().toString());
            return item;
        }, tenantId));
        data.put("policies", jdbcTemplate.query("""
                SELECT policy_id, title, publish_dept, publish_date, content_type FROM cms_policy
                WHERE deleted=0 AND publish_status=1 AND audit_status=1 AND tenant_id=?
                ORDER BY publish_date DESC, policy_id DESC LIMIT 6
                """, (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("policy_id"));
            item.put("title", rs.getString("title"));
            item.put("department", rs.getString("publish_dept"));
            item.put("date", rs.getDate("publish_date") == null ? "" : rs.getDate("publish_date").toString());
            item.put("contentType", rs.getString("content_type"));
            return item;
        }, tenantId));
        return data;
    }

    private static boolean isEmptyHighlights(Map<String, Object> data) {
        return ((java.util.Collection<?>) data.getOrDefault("notices", List.of())).isEmpty()
                && ((java.util.Collection<?>) data.getOrDefault("policies", List.of())).isEmpty();
    }

    @GetMapping("/ai/models")
    @Operation(summary = "门户 AI 助手：可选大模型列表")
    public CommonResult<List<CmsAiChatService.ModelOption>> aiModels() {
        return success(aiChatService.listChatModels());
    }

    @PostMapping("/ai/chat")
    @Operation(summary = "门户 AI 助手：大模型直答（不引用知识库）")
    public CommonResult<CmsAiChatService.ChatResult> aiChat(@Valid @RequestBody AiChatRequest request) {
        List<CmsAiChatService.History> history = request.history() == null ? List.of()
                : request.history().stream()
                        .map(item -> new CmsAiChatService.History(item.getQuestion(), item.getAnswer()))
                        .toList();
        return success(aiChatService.chat(request.question(), history, request.modelId()));
    }

    public record AiChatRequest(@jakarta.validation.constraints.NotBlank
                                @jakarta.validation.constraints.Size(max = 2000) String question,
                                Long modelId,
                                List<CmsLawAnswerReqDTO.HistoryItem> history) { }

    @GetMapping("/overview")
    @Operation(summary = "门户概览：知识库规模统计")
    public CommonResult<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lawChunks", 0L);
        data.put("lawDocuments", 0L);
        data.put("financeChunks", 0L);
        data.put("financeDocuments", 0L);
        ElasticsearchClientProvider provider = elasticsearchProvider.getIfAvailable();
        if (provider != null) {
            try {
                ElasticsearchClient client = provider.client();
                String prefix = searchProperties.getIndexPrefix();
                long lawChunks = count(client, prefix + "-law-read");
                long financeChunks = count(client, prefix + "-finance-policy-read");
                data.put("lawChunks", lawChunks);
                data.put("financeChunks", financeChunks);
                data.put("lawDocuments", distinctDocuments(client, prefix + "-law-read"));
                data.put("financeDocuments", distinctDocuments(client, prefix + "-finance-policy-read"));
            } catch (Exception ex) {
                log.warn("[overview][知识库规模统计失败]", ex);
            }
        }
        data.put("standards", queryCount("SELECT COUNT(*) FROM cms_standard WHERE deleted=0", 0));
        data.put("experts", queryCount("SELECT COUNT(*) FROM cms_expert_profile WHERE deleted=0 AND audit_status=1 AND enabled=1", 1));
        return success(data);
    }

    /**
     * 门户「法规库 / 政策库」列表：不需要关键词，直接按类型列出知识库中的文档标题，
     * 供门户首页展示（个人租户无数据时回退到公司租户）。
     */
    @GetMapping("/law/library")
    @Operation(summary = "门户法规库/政策库：按类型列出已发布文档")
    public CommonResult<List<Map<String, Object>>> lawLibrary(
            @RequestParam(required = false, defaultValue = "LAW") String docCategory,
            @RequestParam(defaultValue = "6") int limit) {
        List<Map<String, Object>> list = List.of();
        ElasticsearchClientProvider provider = elasticsearchProvider.getIfAvailable();
        if (provider != null) {
            list = queryLibrary(provider, docCategory, limit);
        }
        if (list.isEmpty()) {
            list = queryLibraryFromDatabase(docCategory, limit);
        }
        return success(list);
    }

    private List<Map<String, Object>> queryLibrary(ElasticsearchClientProvider provider,
                                                           String docCategory, int limit) {
        String suffix = "FINANCE_POLICY".equalsIgnoreCase(docCategory) ? "-finance-policy-read" : "-law-read";
        try {
            SearchResponse<Map> response = provider.client().search(builder -> builder
                    .index(searchProperties.getIndexPrefix() + suffix)
                    .size(Math.max(1, Math.min(limit, 20)))
                    .query(query -> query.matchAll(matchAll -> matchAll))
                    .collapse(collapse -> collapse.field("title.raw")),
                    Map.class);
            List<Map<String, Object>> list = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", source == null ? null : source.get("title"));
                item.put("docCategory", source == null ? null : source.get("doc_category"));
                item.put("reviewId", source == null ? null : source.get("review_id"));
                item.put("sourceUrl", source == null ? null : source.get("source_url"));
                item.put("issuer", source == null ? null : source.get("issuer"));
                item.put("date", source == null ? null : source.get("publish_date"));
                list.add(item);
            }
            return list;
        } catch (Exception ex) {
            log.warn("[queryLibrary][列表查询失败 docCategory={}]", docCategory, ex);
            return List.of();
        }
    }

    private List<Map<String, Object>> queryLibraryFromDatabase(String docCategory, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        if ("FINANCE_POLICY".equalsIgnoreCase(docCategory)) {
            return queryKnowledgeDocuments(docCategory, safeLimit);
        }
        List<Map<String, Object>> laws = queryKnowledgeDocuments("LAW", safeLimit);
        return laws.isEmpty() ? queryPublishedStandards(safeLimit) : laws;
    }

    private List<Map<String, Object>> queryKnowledgeDocuments(String docCategory, int limit) {
        try {
            return jdbcTemplate.query("""
                    SELECT d.title,
                           d.doc_category AS docCategory,
                           d.review_id AS reviewId,
                           d.source_url AS sourceUrl,
                           COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.snapshot, '$.report.metadata.issuer')), 'null'), '') AS issuer,
                           COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.snapshot, '$.report.metadata.effectiveDate')),
                                    DATE_FORMAT(d.created_at, '%Y-%m-%d')) AS publishDate
                    FROM cms_knowledge_document d
                    LEFT JOIN cms_document_review r
                      ON r.id = d.review_id AND r.tenant_id = d.tenant_id
                    WHERE d.tenant_id = ? AND d.published = 1 AND d.deleted_at IS NULL
                      AND d.doc_category = ?
                    ORDER BY d.created_at DESC
                    LIMIT ?
                    """, (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", rs.getString("title"));
                item.put("docCategory", rs.getString("docCategory"));
                item.put("reviewId", rs.getString("reviewId"));
                item.put("sourceUrl", rs.getString("sourceUrl"));
                item.put("issuer", rs.getString("issuer"));
                item.put("date", rs.getObject("publishDate"));
                return item;
            }, PORTAL_CONTENT_TENANT_ID, docCategory, limit);
        } catch (RuntimeException ex) {
            log.warn("[queryKnowledgeDocuments][数据库回退失败 docCategory={}]", docCategory, ex);
            return List.of();
        }
    }

    private List<Map<String, Object>> queryPublishedStandards(int limit) {
        try {
            return jdbcTemplate.query("""
                    SELECT standard_name AS title,
                           publish_org AS issuer,
                           publish_date AS publishDate,
                           cover_image AS sourceUrl,
                           'LAW' AS docCategory,
                           NULL AS reviewId
                    FROM cms_standard
                    WHERE tenant_id = ? AND deleted = 0 AND status = 1
                      AND audit_status = 1 AND publish_status = 1
                    ORDER BY is_top DESC, publish_date DESC, standard_id DESC
                    LIMIT ?
                    """, (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("title", rs.getString("title"));
                item.put("docCategory", rs.getString("docCategory"));
                item.put("reviewId", rs.getString("reviewId"));
                item.put("sourceUrl", rs.getString("sourceUrl"));
                item.put("issuer", rs.getString("issuer"));
                item.put("date", rs.getObject("publishDate"));
                return item;
            }, PORTAL_CONTENT_TENANT_ID, limit);
        } catch (RuntimeException ex) {
            log.warn("[queryPublishedStandards][数据库回退失败]", ex);
            return List.of();
        }
    }
    private static long count(ElasticsearchClient client, String index) throws java.io.IOException {
        return client.count(builder -> builder.index(index)).count();
    }

    private static long distinctDocuments(ElasticsearchClient client, String index) throws java.io.IOException {
        SearchResponse<Void> response = client.search(builder -> builder.index(index).size(0)
                .aggregations("documents", aggregation -> aggregation.cardinality(card -> card.field("title.raw"))),
                Void.class);
        return response.aggregations().get("documents").cardinality().value();
    }

    private long queryCount(String sql, long fallback) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? fallback : value;
        } catch (RuntimeException ex) {
            log.warn("[queryCount][统计失败 sql={}]", sql, ex);
            return fallback;
        }
    }


    private <T> T executePortalKnowledge(Supplier<T> supplier) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId == PORTAL_CONTENT_TENANT_ID) {
            return supplier.get();
        }
        return TenantUtils.execute(PORTAL_CONTENT_TENANT_ID, supplier::get);
    }

    @GetMapping("/law/search")
    @Operation(summary = "检索已审查且已发布的法规原文")
    public CommonResult<PageResult<CmsLawSearchRespDTO>> search(@Valid CmsLawSearchReqDTO request) {
        return success(executePortalKnowledge(() -> lawApi.search(request)));
    }

    @GetMapping("/law/suggest")
    public CommonResult<List<CmsLawSuggestionDTO>> suggest(@RequestParam String keyword) {
        return success(executePortalKnowledge(() -> lawApi.suggest(keyword,5)));
    }

    @PostMapping("/law/answer")
    @Operation(summary = "基于已发布原文生成带引用的智能回答")
    public CommonResult<CmsLawAnswerRespDTO> answer(@Valid @RequestBody CmsLawAnswerReqDTO request) {
        return success(executePortalKnowledge(() -> ragService.answer(request)));
    }

    @GetMapping("/law/finance-policy/interpretation")
    @Operation(summary = "读取已发布金融政策的结构化 AI 解读")
    public CommonResult<FinancePolicyInterpretationService.InterpretationView> financePolicyInterpretation(
            @RequestParam @jakarta.validation.constraints.Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId) {
        return success(executePortalKnowledge(() -> financePolicyInterpretationService.getPublished(reviewId)));
    }

    @GetMapping("/law/original")
    @Operation(summary = "读取已发布内容的本地化原件")
    public ResponseEntity<?> original(
            @RequestParam @jakarta.validation.constraints.Pattern(regexp = "[a-f0-9\\-]{36}") String id) {
        ReviewService.OriginalFile file = reviewService.publishedOriginalFile(tenant(), id);
        if (file.redirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(file.redirectUrl())).build();
        }
        if (file.content() == null) return ResponseEntity.noContent().build();
        String filename = file.filename() == null || file.filename().isBlank()
                ? "original-" + id + ".bin" : file.filename();
        return ResponseEntity.ok().contentType(originalMediaType(filename))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(file.content());
    }

    private static MediaType originalMediaType(String filename) {
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".txt")) return new MediaType("text", "plain", StandardCharsets.UTF_8);
        if (lower.endsWith(".docx")) return MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @PostMapping("/submission/text")
    @Operation(summary = "提交文本发布申请，自动进入审查")
    public CommonResult<ReviewModels.Report> submitText(@Valid @RequestBody ReviewModels.Submission input) {
        CmsUrlSnapshotService.Snapshot snapshot = snapshotService.consume(tenant(), user(),
                input.sourceSnapshotToken(), input.metadata().sourceUrl());
        byte[] original = snapshot == null ? input.text().getBytes(StandardCharsets.UTF_8) : snapshot.content();
        String filename = snapshot == null ? "正文.txt" : snapshot.filename();
        return success(reviewService.submit(tenant(), user(), input.metadata(),
                new ReviewModels.Parsed(input.text(), List.of()), filename, original, input.chunking()));
    }

    @PostMapping("/submission/url/parse")
    @Operation(summary = "解析政府网站发布原文并提取待确认元数据")
    public CommonResult<PublicationUrlParser.ParseResult> parseUrl(
            @RequestBody @Valid PublicationUrlParserRequest input) {
        PublicationUrlParser.ParseResult result = publicationUrlParser.parse(input.url());
        String token = snapshotService.stage(tenant(), user(), result.metadata().title(),
                result.metadata().sourceUrl(), result.text());
        return success(result.withSnapshotToken(token));
    }

    @PostMapping("/submission/duplicate-check")
    @Operation(summary = "提交发布申请前执行唯一性查重")
    public CommonResult<ReviewModels.DuplicateCheckResult> duplicateCheck(
            @Valid @RequestBody ReviewModels.DuplicateCheckRequest input) {
        return success(reviewService.checkDuplicate(tenant(), input.metadata(), input.text()));
    }

    @PostMapping("/submission/chunk/preview")
    @Operation(summary = "预览文本发布申请的切片效果")
    public CommonResult<ReviewModels.ChunkPreview> previewTextChunks(
            @Valid @RequestBody ReviewModels.ChunkPreviewRequest input) {
        return success(reviewService.preview(input));
    }

    @PostMapping(value = "/submission/chunk/preview-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "预览文件发布申请的切片效果")
    public CommonResult<ReviewModels.ChunkPreview> previewFileChunks(
            @RequestPart("file") MultipartFile file,
            @RequestPart("kind") ReviewModels.Kind kind,
            @Valid @RequestPart(value = "chunking", required = false)
            ReviewModels.ChunkingOptions chunking) throws IOException {
        ReviewModels.Parsed parsed = reviewParser.parse(file);
        return success(reviewService.preview(parsed, chunking, kind));
    }

    @PostMapping(value = "/submission/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档发布申请，自动进入审查")
    public CommonResult<ReviewModels.Report> submitFile(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") ReviewModels.Metadata metadata,
            @Valid @RequestPart(value = "chunking", required = false)
            ReviewModels.ChunkingOptions chunking) throws IOException {
        ReviewModels.Parsed parsed = reviewParser.parse(file);
        byte[] original = null;
        if (file.getSize() <= ReviewParser.MAX_BYTES) {
            try (var stream = file.getInputStream()) {
                original = stream.readNBytes(ReviewParser.MAX_BYTES + 1);
                if (original.length > ReviewParser.MAX_BYTES) original = null;
            }
        }
        return success(reviewService.submit(tenant(), user(), metadata, parsed,
                file.getOriginalFilename(), original, chunking));
    }

    @GetMapping("/submission/mine")
    @Operation(summary = "查询当前企业用户的发布申请")
    public CommonResult<List<ReviewModels.SubmissionStatus>> mine(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return success(reviewService.listBySubmitter(tenant(), user(), limit));
    }

    @GetMapping("/submission/get")
    @Operation(summary = "读取本人发布申请以便修改重提")
    public CommonResult<ReviewModels.Stored> getSubmission(
            @RequestParam @jakarta.validation.constraints.Pattern(regexp = "[a-f0-9\\-]{36}") String id) {
        return success(reviewService.getBySubmitter(tenant(), user(), id));
    }

    @PostMapping("/submission/resubmit")
    @Operation(summary = "修改并重新提交已被人工驳回的发布申请")
    public CommonResult<ReviewModels.Report> resubmit(
            @RequestParam @jakarta.validation.constraints.Pattern(regexp = "[a-f0-9\\-]{36}") String id,
            @Valid @RequestBody ReviewModels.Submission input) {
        return success(reviewService.resubmit(tenant(), user(), id, input));
    }

    private long tenant() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private long user() {
        return getLoginUserId();
    }

    public record PublicationUrlParserRequest(@jakarta.validation.constraints.NotBlank
                                               @jakarta.validation.constraints.Size(max=2000) String url) { }
}
