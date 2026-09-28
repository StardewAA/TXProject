package cn.iocoder.txgy.module.cms.service.rag;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.search.core.HybridSearchCoordinator;
import cn.iocoder.txgy.module.cms.config.CmsRagProperties;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerRespDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.enums.CmsSearchScopeEnum;
import cn.iocoder.txgy.module.cms.service.law.CmsLawSearchServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


/** 法规关键词/向量融合检索及带原文引用的回答。 */
@Service
@Slf4j
public class CmsRagService {

    public static final String VECTOR_COLLECTION = "law";
    private static final int RRF_RANK_CONSTANT = 60;
    private static final Pattern LEADING_POLITE = Pattern.compile("^(?:请问|请|麻烦|帮我|帮忙|想问一下|想了解|了解一下|查一下|查询一下|检索一下|看一下|一下)+");
    private static final Pattern QUESTION_TAIL = Pattern.compile("(?:主要|具体|简要|一般)?(?:规定了什么|规定哪些内容|有哪些规定|包括什么|包含什么|是什么|有哪些|如何规定|怎么规定|如何办理|怎么办理|如何|怎么办|怎么|为什么|是否).*$");

    private final CmsLawSearchServiceImpl lawSearchService;
    private final CmsVectorIndexService vectorIndexService;
    private final ObjectProvider<CmsAnswerGenerator> answerGeneratorProvider;
    private final CmsRagProperties properties;

    public CmsRagService(CmsLawSearchServiceImpl lawSearchService,
                         CmsVectorIndexService vectorIndexService,
                         ObjectProvider<CmsAnswerGenerator> answerGeneratorProvider,
                         CmsRagProperties properties) {
        this.lawSearchService = lawSearchService;
        this.vectorIndexService = vectorIndexService;
        this.answerGeneratorProvider = answerGeneratorProvider;
        this.properties = properties;
    }

    public CmsLawAnswerRespDTO answer(CmsLawAnswerReqDTO request) {
        int topK = request.getTopK() == null ? properties.getTopK() : request.getTopK();
        List<CmsLawAnswerReqDTO.HistoryItem> history = request.getHistory() == null
                ? List.of() : List.copyOf(request.getHistory());
        String contextualQuestion = contextualQuestion(request.getQuestion(), history);
        CmsSearchScopeEnum scope = CmsSearchRouter.resolve(request.getSearchScope(), contextualQuestion);
        SearchBundle bundle = retrieve(contextualQuestion, topK, scope);
        if (bundle.contexts().isEmpty()) {
            return new CmsLawAnswerRespDTO(request.getQuestion(), "现有知识库依据不足，未找到可引用的法规原文。",
                    null, bundle.semanticAvailable(), false, "没有召回结果", scope.name(), List.of());
        }

        CmsAnswerGenerator generator = answerGeneratorProvider.getIfAvailable();
        String answer;
        boolean generated;
        String warning = null;
        if (generator == null) {
            generated = false;
            warning = "大模型未启用，已返回可核验的原文检索结果";
            answer = "已找到相关法规原文，请查看下方引用。当前大模型未启用，因此没有生成归纳性结论。";
        } else {
            try {
                answer = generator.generate(request.getQuestion(), bundle.contexts(), history);
                generated = true;
            } catch (RuntimeException ex) {
                log.warn("qwen-plus answer generation failed: {}", ex.getMessage());
                generated = false;
                warning = "大模型暂时不可用，已降级为原文检索结果";
                answer = "已找到相关法规原文，但大模型本次未能生成回答，请直接核对下方引用。";
            }
        }
        List<CmsLawAnswerRespDTO.Citation> citations = new ArrayList<>();
        for (int i = 0; i < bundle.contexts().size(); i++) {
            CmsLawSearchRespDTO item = bundle.contexts().get(i);
            citations.add(new CmsLawAnswerRespDTO.Citation(i + 1, item.getArticleId(), item.getSourceId(),
                    item.getReviewId(), item.getTitle(), item.getDocumentNo(), item.getArticleNo(), item.getContent(),
                    item.getSourceUrl(), item.getLocalOriginalAvailable(), item.getPageNumber(), item.getScore()));
        }
        return new CmsLawAnswerRespDTO(request.getQuestion(), answer,
                generator == null ? null : generator.modelId(), bundle.semanticAvailable(), generated,
                warning, scope.name(), List.copyOf(citations));
    }

    private String contextualQuestion(String question, List<CmsLawAnswerReqDTO.HistoryItem> history) {
        if (history.isEmpty()) return question;
        StringBuilder contextual = new StringBuilder("对话主题：");
        int start = Math.max(0, history.size() - 3);
        for (int i = start; i < history.size(); i++) {
            contextual.append(history.get(i).getQuestion()).append("；");
        }
        return contextual.append("当前追问：").append(question).toString();
    }

    SearchBundle retrieve(String question, int topK, CmsSearchScopeEnum scope) {
        CmsLawSearchReqDTO keywordRequest = new CmsLawSearchReqDTO();
        keywordRequest.setKeyword(question);
        keywordRequest.setPageNo(1);
        keywordRequest.setPageSize(Math.max(topK * 4, 20));
        Map<String, CmsLawSearchRespDTO> keywordById = new LinkedHashMap<>();

        String keywordCategory = scope == CmsSearchScopeEnum.LAW ? "LAW"
                : scope == CmsSearchScopeEnum.QA ? "QA"
                : scope == CmsSearchScopeEnum.FINANCE_POLICY ? "FINANCE_POLICY" : null;
        if (scope == CmsSearchScopeEnum.LAW) {
            PageResult<CmsLawSearchRespDTO> result = lawSearchService.searchByCategory(keywordRequest, keywordCategory);
            List<CmsLawSearchRespDTO> contexts = result.getList().stream().limit(topK).toList();
            return new SearchBundle(contexts, false);
        }
        if (scope == CmsSearchScopeEnum.QA || scope == CmsSearchScopeEnum.FINANCE_POLICY) {
            String vectorCategory = scope.name();
            try {
                List<String> ids = vectorIndexService.hybridSearch(question, topK, vectorCategory);
                Map<String, CmsLawSearchRespDTO> found = lawSearchService.findPublishedBySourceIds(ids, vectorCategory);
                List<CmsLawSearchRespDTO> contexts = new ArrayList<>();
                for (int i = 0; i < ids.size(); i++) {
                    CmsLawSearchRespDTO item = found.get(ids.get(i));
                    if (item != null) {
                        item.setScore(1.0d / (RRF_RANK_CONSTANT + i + 1));
                        contexts.add(item);
                    }
                }
                return new SearchBundle(List.copyOf(contexts), true);
            } catch (RuntimeException ex) {
                log.warn("[retrieve][{} 向量检索不可用，降级为 BM25]", vectorCategory, ex);
                PageResult<CmsLawSearchRespDTO> result = lawSearchService.searchByCategory(keywordRequest, vectorCategory);
                return new SearchBundle(result.getList().stream().limit(topK).toList(), false);
            }
        }

        // 自然语言问句先抽取主题词（例如“安全生产法主要规定了什么？” -> “安全生产法”），
        // 主题词命中结果放在关键词列表前面，优先召回法规原文，避免被同词金融政策或技术资料抢占。
        for (CmsLawSearchRespDTO item : subjectRecall(question, topK)) {
            keywordById.put(item.getSourceId(), item);
        }

        HybridSearchCoordinator.Result fused = HybridSearchCoordinator.search(
                () -> {
                    PageResult<CmsLawSearchRespDTO> result = lawSearchService.searchByCategory(keywordRequest, keywordCategory);
                    for (CmsLawSearchRespDTO item : result.getList()) {
                        keywordById.put(item.getSourceId(), item);
                    }
                    return new ArrayList<>(keywordById.keySet());
                },
                () -> semanticSourceIds(question, topK * 4), RRF_RANK_CONSTANT, topK);

        List<String> ids = fused.hits().stream().map(hit -> hit.sourceId()).toList();
        Map<String, CmsLawSearchRespDTO> all = new LinkedHashMap<>(keywordById);
        all.putAll(lawSearchService.findPublishedBySourceIds(ids, keywordCategory));
        List<CmsLawSearchRespDTO> contexts = new ArrayList<>();
        fused.hits().forEach(hit -> {
            CmsLawSearchRespDTO item = all.get(hit.sourceId());
            if (item != null) {
                item.setScore(hit.score());
                contexts.add(item);
            }
        });
        return new SearchBundle(List.copyOf(contexts), fused.semanticAvailable());
    }

    private List<CmsLawSearchRespDTO> subjectRecall(String question, int topK) {
        String subject = extractQuestionSubject(question);
        String normalizedQuestion = question == null ? "" : question.strip();
        if (subject.isBlank() || subject.equals(normalizedQuestion)) return List.of();
        CmsLawSearchReqDTO request = new CmsLawSearchReqDTO();
        request.setKeyword(subject);
        request.setPageNo(1);
        request.setPageSize(Math.max(topK * 3, 15));
        String category = subjectCategory(subject);
        try {
            return lawSearchService.searchByCategory(request, category).getList();
        } catch (RuntimeException ex) {
            log.warn("[subjectRecall][主题词召回失败 subject={} category={}]", subject, category, ex);
            return List.of();
        }
    }

    /** 将自然语言提问退化为可检索的主题词，保留法规名、政策对象等核心信息。 */
    static String extractQuestionSubject(String question) {
        String value = question == null ? "" : question.strip();
        if (value.isBlank()) return "";
        value = LEADING_POLITE.matcher(value).replaceFirst("");
        value = QUESTION_TAIL.matcher(value).replaceFirst("");
        value = value.replace("《", "").replace("》", "")
                .replaceAll("[\\s\\u3000，,。；;：:！!？?]+", " ")
                .strip();
        return value.length() >= 2 ? value : "";
    }

    private static String subjectCategory(String subject) {
        if (endsWithAny(subject, "法", "法典", "条例", "规定", "办法", "标准", "规范")) return "LAW";
        if (containsAny(subject, "补贴", "政策", "申报", "奖补", "贴息", "融资", "贷款", "税费", "资金")) {
            return "FINANCE_POLICY";
        }
        return null;
    }

    private static boolean endsWithAny(String value, String... suffixes) {
        for (String suffix : suffixes) if (value.endsWith(suffix)) return true;
        return false;
    }

    private static boolean containsAny(String value, String... parts) {
        for (String part : parts) if (value.contains(part)) return true;
        return false;
    }

    private List<String> semanticSourceIds(String question, int topK) {
        return vectorIndexService.search(question, topK, properties.getSimilarityThreshold(), null);
    }

    record SearchBundle(List<CmsLawSearchRespDTO> contexts, boolean semanticAvailable) { }

}
