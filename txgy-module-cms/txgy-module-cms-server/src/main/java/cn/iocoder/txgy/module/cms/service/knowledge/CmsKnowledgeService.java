package cn.iocoder.txgy.module.cms.service.knowledge;

import cn.iocoder.txgy.framework.search.config.ElasticsearchSearchProperties;
import cn.iocoder.txgy.framework.search.core.ElasticsearchClientProvider;
import cn.iocoder.txgy.framework.search.core.RedisVectorSearchOperations;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.cms.service.rag.CmsRagService;
import cn.iocoder.txgy.module.cms.service.rag.CmsVectorIndexService;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.KNOWLEDGE_DOCUMENT_NOT_FOUND;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.LAW_SEARCH_UNAVAILABLE;

/** 管理员知识库清单、下架及 MySQL 到检索索引的可重复同步。 */
@Service
@Slf4j
public class CmsKnowledgeService {

    private static final Pattern ARTICLE = Pattern.compile("(?m)^\\s*(第[一二三四五六七八九十百千万零〇]+条|\\d+(?:\\.\\d+)+)");
    private static final String SOURCE_PREFIX = "cms-kb:";

    private final JdbcTemplate jdbc;
    private final ObjectProvider<ElasticsearchClientProvider> elasticsearchProvider;
    private final ObjectProvider<RedisVectorSearchOperations> vectorProvider;
    private final ElasticsearchSearchProperties searchProperties;
    private final CmsVectorIndexService vectorIndexService;

    public CmsKnowledgeService(JdbcTemplate jdbc,
                               ObjectProvider<ElasticsearchClientProvider> elasticsearchProvider,
                               ObjectProvider<RedisVectorSearchOperations> vectorProvider,
                               ElasticsearchSearchProperties searchProperties,
                               CmsVectorIndexService vectorIndexService) {
        this.jdbc = jdbc;
        this.elasticsearchProvider = elasticsearchProvider;
        this.vectorProvider = vectorProvider;
        this.searchProperties = searchProperties;
        this.vectorIndexService = vectorIndexService;
    }

    public List<KnowledgeItem> list() {
        return list(false);
    }

    public List<KnowledgeItem> listArchived() {
        return list(true);
    }

    private List<KnowledgeItem> list(boolean archived) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        String archiveCondition = archived ? "d.deleted_at IS NOT NULL" : "d.deleted_at IS NULL AND d.published=1";
        String sql = """
                        SELECT d.review_id,d.title,d.validity,d.source_url,d.text_hash,d.published,d.created_at,
                               COUNT(c.ordinal),d.deleted_at
                        FROM cms_knowledge_document d LEFT JOIN cms_knowledge_chunk c
                          ON c.tenant_id=d.tenant_id AND c.review_id=d.review_id
                        WHERE d.tenant_id=? AND %s
                        GROUP BY d.review_id,d.title,d.validity,d.source_url,d.text_hash,d.published,d.created_at,d.deleted_at
                        ORDER BY d.created_at DESC,d.review_id DESC LIMIT 200
                        """.formatted(archiveCondition);
        return jdbc.query(sql,
                (rs, row) -> new KnowledgeItem(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getBoolean(6),
                        rs.getTimestamp(7).toLocalDateTime(), rs.getInt(8),rs.getTimestamp(9)!=null,
                        rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toLocalDateTime()), tenantId);
    }

    /** 只有官方核验通过后进入知识库的文档才能发布。 */
    @Transactional(rollbackFor = Exception.class)
    public PublishResult publish(String reviewId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cms_knowledge_document WHERE tenant_id=? AND review_id=?",
                Integer.class, tenantId, reviewId);
        if (count == null || count == 0) {
            throw exception(KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        jdbc.update("UPDATE cms_knowledge_document SET published=1,deleted_at=NULL WHERE tenant_id=? AND review_id=?",
                tenantId, reviewId);
        List<IndexRecord> records = loadRecords(tenantId, reviewId);
        indexRecords(records);
        int vectorChunks = vectorIndexService.indexPublished(records.stream().map(IndexRecord::sourceId).toList());
        return new PublishResult(reviewId, true, records.size(), vectorChunks != records.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(String reviewId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<Integer> ordinals = jdbc.queryForList(
                "SELECT ordinal FROM cms_knowledge_chunk WHERE tenant_id=? AND review_id=?", Integer.class,
                tenantId, reviewId);
        int updated = jdbc.update("UPDATE cms_knowledge_document SET published=0,deleted_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND review_id=? AND published=1",
                tenantId, reviewId);
        if (updated == 0) {
            return;
        }
        deleteFromElasticsearch(tenantId, sourcePrefix(tenantId, reviewId));
        RedisVectorSearchOperations vectors = vectorProvider.getIfAvailable();
        if (vectors != null) {
            ordinals.forEach(ordinal -> vectors.delete(CmsRagService.VECTOR_COLLECTION,
                    tenantId, sourceId(tenantId, reviewId, ordinal)));
        }
    }

    public void unpublish(String reviewId) { archive(reviewId); }

    public ReindexResult rebuildSearchIndex() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        deleteFromElasticsearch(tenantId, SOURCE_PREFIX + tenantId + ":");
        List<IndexRecord> records = loadPublishedRecords(tenantId);
        indexRecords(records);
        CmsVectorIndexService.RebuildResult vector = vectorIndexService.rebuild();
        return new ReindexResult(records.size(), vector.indexed(), vector.embeddingModel());
    }

    private List<IndexRecord> loadPublishedRecords(long tenantId) {
        return loadRecords(tenantId, null);
    }

    private List<IndexRecord> loadRecords(long tenantId, String reviewIdFilter) {
        Map<String, ReviewModels.Stored> reviews = new LinkedHashMap<>();
        jdbc.query("SELECT id,snapshot FROM cms_document_review WHERE tenant_id=?",
                        (rs, row) -> Map.entry(rs.getString(1),
                                JsonUtils.parseObject(rs.getString(2), ReviewModels.Stored.class)), tenantId)
                .forEach(entry -> reviews.put(entry.getKey(), entry.getValue()));
        String sql = """
                        SELECT d.review_id,d.text_hash,c.ordinal,c.content
                        FROM cms_knowledge_document d JOIN cms_knowledge_chunk c
                          ON c.tenant_id=d.tenant_id AND c.review_id=d.review_id
                        WHERE d.tenant_id=? AND d.published=1
                        """ + (reviewIdFilter == null ? "" : " AND d.review_id=?")
                        + " ORDER BY d.review_id,c.ordinal";
        Object[] args = reviewIdFilter == null ? new Object[]{tenantId} : new Object[]{tenantId, reviewIdFilter};
        return jdbc.query(sql, (rs, row) -> {
            String reviewId = rs.getString(1);
            ReviewModels.Stored stored = reviews.get(reviewId);
            if (stored == null) {
                throw new IllegalStateException("知识库文档缺少审查快照：" + reviewId);
            }
            ReviewModels.Metadata metadata = stored.report().metadata();
            int ordinal = rs.getInt(3);
            String content = rs.getString(4);
            String sourceId = sourceId(tenantId, reviewId, ordinal);
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("tenant_id", String.valueOf(tenantId));
            document.put("law_id", stableId(reviewId));
            document.put("article_id", stableId(sourceId));
            document.put("source_id", sourceId);
            document.put("version", rs.getString(2).substring(0, 12));
            document.put("title", metadata.title());
            document.put("document_no", metadata.documentNo());
            document.put("document_no_normalized", normalize(metadata.documentNo()));
            String articleNo = articleNo(content, ordinal);
            document.put("article_no", articleNo);
            document.put("article_no_normalized", normalize(articleNo));
            document.put("content", content);
            document.put("issuing_authority", metadata.issuer());
            document.put("law_type", metadata.kind().name());
            document.put("status", metadata.validity().name());
            document.put("source_url", metadata.sourceUrl());
            if (metadata.effectiveDate() != null) document.put("effective_date", metadata.effectiveDate().toString());
            if (metadata.expiryDate() != null) document.put("expiry_date", metadata.expiryDate().toString());
            document.put("published", true);
            document.put("revision", 1);
            return new IndexRecord(sourceId, document);
        }, args);
    }

    private void indexRecords(List<IndexRecord> records) {
        ElasticsearchClient client = requiredClient();
        try {
            for (int offset = 0; offset < records.size(); offset += 200) {
                List<IndexRecord> batch = records.subList(offset, Math.min(offset + 200, records.size()));
                var response = client.bulk(builder -> {
                    for (IndexRecord record : batch) {
                        builder.operations(operation -> operation.index(index -> index
                                .index(writeAlias()).id(record.sourceId()).document(record.document())));
                    }
                    return builder;
                });
                if (response.errors()) {
                    throw new IllegalStateException("Elasticsearch 批量写入存在失败项");
                }
            }
        } catch (IOException ex) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    private void deleteFromElasticsearch(long tenantId, String sourcePrefix) {
        Query query = Query.of(builder -> builder.bool(bool -> bool
                .filter(filter -> filter.term(term -> term.field("tenant_id").value(String.valueOf(tenantId))))
                .filter(filter -> filter.prefix(prefix -> prefix.field("source_id").value(sourcePrefix)))));
        try {
            requiredClient().deleteByQuery(builder -> builder.index(readAlias()).query(query).refresh(true));
        } catch (IOException ex) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    private ElasticsearchClient requiredClient() {
        ElasticsearchClientProvider provider = elasticsearchProvider.getIfAvailable();
        if (provider == null) throw exception(LAW_SEARCH_UNAVAILABLE);
        return provider.client();
    }

    private String readAlias() { return searchProperties.getIndexPrefix() + "-law-read"; }
    private String writeAlias() { return searchProperties.getIndexPrefix() + "-law-write"; }
    private static String sourcePrefix(long tenantId, String reviewId) { return SOURCE_PREFIX + tenantId + ":" + reviewId + ":"; }
    private static String sourceId(long tenantId, String reviewId, int ordinal) { return sourcePrefix(tenantId, reviewId) + ordinal; }
    private static String articleNo(String content, int ordinal) {
        Matcher matcher = ARTICLE.matcher(content);
        return matcher.find() ? matcher.group(1) : "原文片段" + ordinal;
    }
    private static String normalize(String value) {
        return value == null ? "" : value.toUpperCase().replaceAll("[\\s·•—–－_\\-]+", "");
    }
    private static long stableId(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Long.parseLong(HexFormat.of().formatHex(hash, 0, 7), 16) & 0x1F_FFFF_FFFF_FFFFL;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record IndexRecord(String sourceId, Map<String, Object> document) { }
    public record KnowledgeItem(String reviewId, String title, String validity, String sourceUrl,
                                String textHash, boolean published, LocalDateTime createdAt, int chunkCount,
                                boolean archived, LocalDateTime archivedAt) { }
    public record PublishResult(String reviewId, boolean published, int keywordChunks,
                                boolean vectorRebuildRequired) { }
    public record ReindexResult(int keywordChunks, int vectorChunks, String embeddingModel) { }

}
