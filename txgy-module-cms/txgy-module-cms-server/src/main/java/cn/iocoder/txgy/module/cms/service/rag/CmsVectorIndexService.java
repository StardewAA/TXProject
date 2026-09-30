package cn.iocoder.txgy.module.cms.service.rag;

import cn.iocoder.txgy.framework.search.core.TextEmbeddingProvider;
import cn.iocoder.txgy.framework.search.core.ReciprocalRankFusion;
import cn.iocoder.txgy.framework.search.config.ElasticsearchSearchProperties;
import cn.iocoder.txgy.framework.search.core.ElasticsearchClientProvider;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.config.CmsRagProperties;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.service.law.CmsLawSearchServiceImpl;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.LAW_SEARCH_UNAVAILABLE;

/** 问答库 ES dense_vector 索引。Redis 仅保留兼容代码，不再承担召回。 */
@Service
@Slf4j
public class CmsVectorIndexService {

    private final CmsLawSearchServiceImpl lawSearchService;
    private final ObjectProvider<TextEmbeddingProvider> embeddingProvider;
    private final ObjectProvider<ElasticsearchClientProvider> elasticsearchProvider;
    private final ElasticsearchSearchProperties searchProperties;
    private final CmsRagProperties ragProperties;

    public CmsVectorIndexService(CmsLawSearchServiceImpl lawSearchService,
                                 ObjectProvider<TextEmbeddingProvider> embeddingProvider,
                                 ObjectProvider<ElasticsearchClientProvider> elasticsearchProvider,
                                 ElasticsearchSearchProperties searchProperties,
                                 CmsRagProperties ragProperties) {
        this.lawSearchService = lawSearchService;
        this.embeddingProvider = embeddingProvider;
        this.elasticsearchProvider = elasticsearchProvider;
        this.searchProperties = searchProperties;
        this.ragProperties = ragProperties;
    }

    public RebuildResult rebuild() {
        TextEmbeddingProvider embedding = embeddingProvider.getIfAvailable();
        if (embedding == null) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<CmsLawSearchRespDTO> documents = new ArrayList<>();
        documents.addAll(lawSearchService.listPublishedForVectorIndex("QA", 10_000));
        documents.addAll(lawSearchService.listPublishedForVectorIndex("FINANCE_POLICY", 10_000));
        int indexed = indexDocuments(documents, embedding);
        String model = embedding instanceof CmsBatchEmbeddingProvider batchProvider
                ? batchProvider.embeddingModelId() : embedding.modelId();
        return new RebuildResult(indexed, model, embedding.dimensions());
    }

    /** 嵌入模型（RAG）是否已就绪；未配置时仅重建关键词索引。 */
    public boolean isEmbeddingAvailable() {
        return embeddingProvider.getIfAvailable() != null;
    }

    /** 发布时增量写入本次文档的向量；模型暂不可用时保留关键词发布，由重建任务补偿。 */
    public int indexPublished(List<String> sourceIds) {
        TextEmbeddingProvider embedding = embeddingProvider.getIfAvailable();
        if (embedding == null || sourceIds == null || sourceIds.isEmpty()) {
            return 0;
        }
        long tenantId = TenantContextHolder.getRequiredTenantId();
        try {
            Map<String, CmsLawSearchRespDTO> found = lawSearchService.findPublishedBySourceIds(sourceIds, null);
            List<CmsLawSearchRespDTO> documents = new ArrayList<>(sourceIds.size());
            for (String sourceId : sourceIds) {
                CmsLawSearchRespDTO document = found.get(sourceId);
                if (document != null && isVectorCategory(document.getDocCategory())) documents.add(document);
            }
            return indexDocuments(documents, embedding);
        } catch (RuntimeException ex) {
            log.warn("[indexPublished][发布内容向量增量写入失败，tenantId={}, sourceCount={}]",
                    tenantId, sourceIds.size(), ex);
            return 0;
        }
    }

    /**
     * 对单个已发布切片重新生成向量并覆盖索引。这里不吞掉异常：内容管理修改必须在
     * 向量生成失败时回滚 MySQL，避免文本与向量长期不一致。
     */
    public void upsertPublishedText(long tenantId, String sourceId, String version, String content,
                                    String docCategory) {
        TextEmbeddingProvider embedding = embeddingProvider.getIfAvailable();
        if (embedding == null) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
        float[] values = embedding.embed(content);
        updateEmbedding(sourceId, values, docCategory);
    }

    public void deletePublishedText(long tenantId, String sourceId) {
        // ES 文档与向量同生共灭，实际删除由 CmsKnowledgeService 按 source_id 完成。
    }

    public List<String> search(String query, int topK, double similarityThreshold) {
        return search(query, topK, similarityThreshold, "QA");
    }

    public List<String> search(String query, int topK, double similarityThreshold, String docCategory) {
        TextEmbeddingProvider embedding = embeddingProvider.getIfAvailable();
        if (embedding == null) throw exception(LAW_SEARCH_UNAVAILABLE);
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<Float> queryVector = boxed(embedding.embed(query));
        try {
            SearchResponse<Map> response = requiredClient().search(builder -> builder
                            .index(vectorReadAliases(docCategory))
                            .size(topK)
                            .knn(knn -> knn.field("embedding")
                                    .queryVector(queryVector)
                                    .k(topK)
                                    .numCandidates(Math.max(50, topK * 10))
                                    .filter(filter -> filter.bool(bool -> bool
                                            .filter(term -> term.term(value -> value.field("tenant_id")
                                                    .value(String.valueOf(tenantId))))
                                            .filter(term -> term.term(value -> value.field("published").value(true)))))),
                    Map.class);
            double minimumScore = (1.0d + similarityThreshold) / 2.0d;
            return response.hits().hits().stream()
                    .filter(hit -> hit.score() == null || hit.score() >= minimumScore)
                    .map(Hit::source)
                    .filter(source -> source != null && source.get("source_id") != null)
                    .map(source -> String.valueOf(source.get("source_id")))
                    .toList();
        } catch (IOException ex) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    /** QA 库在 Elasticsearch 内部完成 BM25 + kNN 原生 RRF。 */
    public List<String> hybridSearch(String query, int topK) {
        return hybridSearch(query, topK, "QA");
    }

    public List<String> hybridSearch(String query, int topK, String docCategory) {
        if (!ragProperties.isNativeRrfEnabled()) {
            return applicationRrfSearch(query, topK, docCategory);
        }
        TextEmbeddingProvider embedding = embeddingProvider.getIfAvailable();
        if (embedding == null) throw exception(LAW_SEARCH_UNAVAILABLE);
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<Float> queryVector = boxed(embedding.embed(query));
        try {
            SearchResponse<Map> response = requiredClient().search(builder -> builder
                            .index(vectorReadAliases(docCategory))
                            .size(topK)
                            .query(search -> search.bool(bool -> bool
                                    .filter(term -> term.term(value -> value.field("tenant_id")
                                            .value(String.valueOf(tenantId))))
                                    .filter(term -> term.term(value -> value.field("published").value(true)))
                                    .must(must -> must.multiMatch(match -> match.query(query)
                                            .fields("title^3", "content^2", "document_no^4")))))
                            .knn(knn -> knn.field("embedding")
                                    .queryVector(queryVector)
                                    .k(Math.max(topK, 10))
                                    .numCandidates(Math.max(50, topK * 10))
                                    .filter(filter -> filter.bool(bool -> bool
                                            .filter(term -> term.term(value -> value.field("tenant_id")
                                                    .value(String.valueOf(tenantId))))
                                            .filter(term -> term.term(value -> value.field("published").value(true))))))
                            .rank(rank -> rank.rrf(rrf -> rrf.rankConstant(60L)
                                    .rankWindowSize((long) Math.max(20, topK * 4)))),
                    Map.class);
            return sourceIds(response);
        } catch (IOException | RuntimeException ex) {
            log.warn("[hybridSearch][ES 原生 RRF 不可用，降级为应用层 RRF，tenantId={}]", tenantId, ex);
            return applicationRrfSearch(query, topK, docCategory);
        }
    }

    private List<String> applicationRrfSearch(String query, int topK, String docCategory) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        try {
            SearchResponse<Map> keywordResponse = requiredClient().search(builder -> builder
                            .index(vectorReadAliases(docCategory))
                            .size(Math.max(20, topK * 4))
                            .query(search -> search.bool(bool -> bool
                                    .filter(term -> term.term(value -> value.field("tenant_id")
                                            .value(String.valueOf(tenantId))))
                                    .filter(term -> term.term(value -> value.field("published").value(true)))
                                    .must(must -> must.multiMatch(match -> match.query(query)
                                            .fields("title^3", "content^2", "document_no^4"))))),
                    Map.class);
            List<String> keywordIds = sourceIds(keywordResponse);
            List<String> vectorIds = search(query, Math.max(20, topK * 4), 0.0d, docCategory);
            return ReciprocalRankFusion.fuse(keywordIds, vectorIds, 60, topK).stream()
                    .map(ReciprocalRankFusion.FusedHit::sourceId)
                    .toList();
        } catch (IOException ex) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    private int indexDocuments(List<CmsLawSearchRespDTO> documents, TextEmbeddingProvider embedding) {
        int batchSize = 10;
        int indexed = 0;
        for (int offset = 0; offset < documents.size(); offset += batchSize) {
            List<CmsLawSearchRespDTO> batch = documents.subList(offset, Math.min(offset + batchSize, documents.size()));
            List<String> texts = batch.stream().map(CmsLawSearchRespDTO::getContent).toList();
            List<float[]> embeddings = embedding instanceof CmsBatchEmbeddingProvider batchProvider
                    ? batchProvider.embedAll(texts)
                    : texts.stream().map(embedding::embed).toList();
            if (embeddings.size() != batch.size()) {
                throw new IllegalStateException("向量数量与原文片段数量不一致");
            }
            for (int i = 0; i < batch.size(); i++) {
                CmsLawSearchRespDTO item = batch.get(i);
                updateEmbedding(item.getSourceId(), embeddings.get(i), item.getDocCategory());
                indexed++;
            }
        }
        return indexed;
    }

    private void updateEmbedding(String sourceId, float[] embedding, String docCategory) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("embedding", boxed(embedding));
        try {
            requiredClient().update(builder -> builder.index(vectorWriteAlias(docCategory)).id(sourceId).doc(patch), Map.class);
        } catch (IOException ex) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    private static List<Float> boxed(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) result.add(value);
        return result;
    }

    private static List<String> sourceIds(SearchResponse<Map> response) {
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(source -> source != null && source.get("source_id") != null)
                .map(source -> String.valueOf(source.get("source_id")))
                .toList();
    }

    private ElasticsearchClient requiredClient() {
        ElasticsearchClientProvider provider = elasticsearchProvider.getIfAvailable();
        if (provider == null) throw exception(LAW_SEARCH_UNAVAILABLE);
        return provider.client();
    }

    private List<String> vectorReadAliases(String docCategory) {
        if ("FINANCE_POLICY".equalsIgnoreCase(docCategory)) {
            return List.of(searchProperties.getIndexPrefix() + "-finance-policy-read");
        }
        if ("QA".equalsIgnoreCase(docCategory)) {
            return List.of(searchProperties.getIndexPrefix() + "-qa-read");
        }
        return List.of(searchProperties.getIndexPrefix() + "-qa-read",
                searchProperties.getIndexPrefix() + "-finance-policy-read");
    }

    private String vectorWriteAlias(String docCategory) {
        return searchProperties.getIndexPrefix()
                + ("FINANCE_POLICY".equalsIgnoreCase(docCategory) ? "-finance-policy-write" : "-qa-write");
    }

    private static boolean isVectorCategory(String docCategory) {
        return "QA".equalsIgnoreCase(docCategory) || "FINANCE_POLICY".equalsIgnoreCase(docCategory);
    }

    public record RebuildResult(int indexed, String embeddingModel, int dimensions) { }

}
