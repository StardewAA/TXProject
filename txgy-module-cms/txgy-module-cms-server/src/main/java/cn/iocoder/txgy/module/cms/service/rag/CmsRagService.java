package cn.iocoder.txgy.module.cms.service.rag;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.search.core.HybridSearchCoordinator;
import cn.iocoder.txgy.framework.search.core.RedisVectorSearchOperations;
import cn.iocoder.txgy.framework.search.core.TextEmbeddingProvider;
import cn.iocoder.txgy.framework.search.core.VectorSearchHit;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.config.CmsRagProperties;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerRespDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.service.law.CmsLawSearchServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.LAW_SEARCH_UNAVAILABLE;

/** 法规关键词/向量融合检索及带原文引用的回答。 */
@Service
@Slf4j
public class CmsRagService {

    public static final String VECTOR_COLLECTION = "law";
    private static final int RRF_RANK_CONSTANT = 60;

    private final CmsLawSearchServiceImpl lawSearchService;
    private final ObjectProvider<TextEmbeddingProvider> embeddingProvider;
    private final ObjectProvider<RedisVectorSearchOperations> vectorSearchProvider;
    private final ObjectProvider<CmsAnswerGenerator> answerGeneratorProvider;
    private final CmsRagProperties properties;

    public CmsRagService(CmsLawSearchServiceImpl lawSearchService,
                         ObjectProvider<TextEmbeddingProvider> embeddingProvider,
                         ObjectProvider<RedisVectorSearchOperations> vectorSearchProvider,
                         ObjectProvider<CmsAnswerGenerator> answerGeneratorProvider,
                         CmsRagProperties properties) {
        this.lawSearchService = lawSearchService;
        this.embeddingProvider = embeddingProvider;
        this.vectorSearchProvider = vectorSearchProvider;
        this.answerGeneratorProvider = answerGeneratorProvider;
        this.properties = properties;
    }

    public CmsLawAnswerRespDTO answer(CmsLawAnswerReqDTO request) {
        int topK = request.getTopK() == null ? properties.getTopK() : request.getTopK();
        SearchBundle bundle = retrieve(request.getQuestion(), topK);
        if (bundle.contexts().isEmpty()) {
            return new CmsLawAnswerRespDTO(request.getQuestion(), "现有知识库依据不足，未找到可引用的法规原文。",
                    null, bundle.semanticAvailable(), false, "没有召回结果", List.of());
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
                answer = generator.generate(request.getQuestion(), bundle.contexts());
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
                    item.getTitle(), item.getDocumentNo(), item.getArticleNo(), item.getContent(),
                    item.getSourceUrl(), item.getScore()));
        }
        return new CmsLawAnswerRespDTO(request.getQuestion(), answer,
                generator == null ? null : generator.modelId(), bundle.semanticAvailable(), generated,
                warning, List.copyOf(citations));
    }

    SearchBundle retrieve(String question, int topK) {
        CmsLawSearchReqDTO keywordRequest = new CmsLawSearchReqDTO();
        keywordRequest.setKeyword(question);
        keywordRequest.setPageNo(1);
        keywordRequest.setPageSize(Math.max(topK * 4, 20));
        Map<String, CmsLawSearchRespDTO> keywordById = new LinkedHashMap<>();

        HybridSearchCoordinator.Result fused = HybridSearchCoordinator.search(
                () -> {
                    PageResult<CmsLawSearchRespDTO> result = lawSearchService.search(keywordRequest);
                    for (CmsLawSearchRespDTO item : result.getList()) {
                        keywordById.put(item.getSourceId(), item);
                    }
                    return new ArrayList<>(keywordById.keySet());
                },
                () -> semanticSourceIds(question, topK * 4), RRF_RANK_CONSTANT, topK);

        List<String> ids = fused.hits().stream().map(hit -> hit.sourceId()).toList();
        Map<String, CmsLawSearchRespDTO> all = new LinkedHashMap<>(keywordById);
        all.putAll(lawSearchService.findPublishedBySourceIds(ids));
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

    private List<String> semanticSourceIds(String question, int topK) {
        TextEmbeddingProvider embedding = embeddingProvider.getIfAvailable();
        RedisVectorSearchOperations vectors = vectorSearchProvider.getIfAvailable();
        if (embedding == null || vectors == null) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return vectors.search(VECTOR_COLLECTION, tenantId, embedding.embed(question), topK).stream()
                .filter(hit -> 1.0d - hit.distance() >= properties.getSimilarityThreshold())
                .map(VectorSearchHit::sourceId)
                .toList();
    }

    record SearchBundle(List<CmsLawSearchRespDTO> contexts, boolean semanticAvailable) { }

}
