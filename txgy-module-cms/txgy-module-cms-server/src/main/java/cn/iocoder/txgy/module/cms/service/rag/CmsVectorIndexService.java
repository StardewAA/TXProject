package cn.iocoder.txgy.module.cms.service.rag;

import cn.iocoder.txgy.framework.search.core.RedisVectorDocument;
import cn.iocoder.txgy.framework.search.core.RedisVectorSearchOperations;
import cn.iocoder.txgy.framework.search.core.TextEmbeddingProvider;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.service.law.CmsLawSearchServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.LAW_SEARCH_UNAVAILABLE;

/** 从 Elasticsearch 已发布原文重建 Redis 向量索引。 */
@Service
@Slf4j
public class CmsVectorIndexService {

    private final CmsLawSearchServiceImpl lawSearchService;
    private final ObjectProvider<TextEmbeddingProvider> embeddingProvider;
    private final ObjectProvider<RedisVectorSearchOperations> vectorSearchProvider;

    public CmsVectorIndexService(CmsLawSearchServiceImpl lawSearchService,
                                 ObjectProvider<TextEmbeddingProvider> embeddingProvider,
                                 ObjectProvider<RedisVectorSearchOperations> vectorSearchProvider) {
        this.lawSearchService = lawSearchService;
        this.embeddingProvider = embeddingProvider;
        this.vectorSearchProvider = vectorSearchProvider;
    }

    public RebuildResult rebuild() {
        TextEmbeddingProvider embedding = embeddingProvider.getIfAvailable();
        RedisVectorSearchOperations vectors = vectorSearchProvider.getIfAvailable();
        if (embedding == null || vectors == null) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
        long tenantId = TenantContextHolder.getRequiredTenantId();
        vectors.ensureIndex(CmsRagService.VECTOR_COLLECTION);
        List<CmsLawSearchRespDTO> documents = lawSearchService.listPublishedForVectorIndex(10_000);
        int indexed = indexDocuments(tenantId, documents, embedding, vectors);
        return new RebuildResult(indexed, embedding.modelId(), embedding.dimensions());
    }

    /** 发布时增量写入本次文档的向量；模型暂不可用时保留关键词发布，由重建任务补偿。 */
    public int indexPublished(List<String> sourceIds) {
        TextEmbeddingProvider embedding = embeddingProvider.getIfAvailable();
        RedisVectorSearchOperations vectors = vectorSearchProvider.getIfAvailable();
        if (embedding == null || vectors == null || sourceIds == null || sourceIds.isEmpty()) {
            return 0;
        }
        long tenantId = TenantContextHolder.getRequiredTenantId();
        try {
            vectors.ensureIndex(CmsRagService.VECTOR_COLLECTION);
            Map<String, CmsLawSearchRespDTO> found = lawSearchService.findPublishedBySourceIds(sourceIds);
            List<CmsLawSearchRespDTO> documents = new ArrayList<>(sourceIds.size());
            for (String sourceId : sourceIds) {
                CmsLawSearchRespDTO document = found.get(sourceId);
                if (document != null) documents.add(document);
            }
            return indexDocuments(tenantId, documents, embedding, vectors);
        } catch (RuntimeException ex) {
            log.warn("[indexPublished][发布内容向量增量写入失败，tenantId={}, sourceCount={}]",
                    tenantId, sourceIds.size(), ex);
            return 0;
        }
    }

    private static int indexDocuments(long tenantId, List<CmsLawSearchRespDTO> documents,
                                      TextEmbeddingProvider embedding, RedisVectorSearchOperations vectors) {
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
                vectors.upsert(CmsRagService.VECTOR_COLLECTION,
                        new RedisVectorDocument(tenantId, item.getSourceId(), item.getVersion(), embeddings.get(i)));
                indexed++;
            }
        }
        return indexed;
    }

    public record RebuildResult(int indexed, String embeddingModel, int dimensions) { }

}
