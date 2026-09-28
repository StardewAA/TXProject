package cn.iocoder.txgy.module.cms.service.knowledge;

import cn.iocoder.txgy.framework.search.config.ElasticsearchSearchProperties;
import cn.iocoder.txgy.framework.search.core.ElasticsearchClientProvider;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.cms.service.rag.CmsVectorIndexService;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
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
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.KNOWLEDGE_PERMANENT_DELETE_NOT_ALLOWED;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.KNOWLEDGE_CHUNK_NOT_FOUND;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.KNOWLEDGE_CHUNK_SYNC_FAILED;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.KNOWLEDGE_CHUNK_VERSION_CONFLICT;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.LAW_SEARCH_UNAVAILABLE;

/** 管理员知识库清单、下架及 MySQL 到检索索引的可重复同步。 */
@Service
@Slf4j
public class CmsKnowledgeService {

    private static final Pattern ARTICLE = Pattern.compile("(?m)^\\s*(第[一二三四五六七八九十百千万零〇]+条|\\d+(?:\\.\\d+)+)");
    private static final String SOURCE_PREFIX = "cms-kb:";

    private final JdbcTemplate jdbc;
    private final ObjectProvider<ElasticsearchClientProvider> elasticsearchProvider;
    private final ElasticsearchSearchProperties searchProperties;
    private final CmsVectorIndexService vectorIndexService;

    public CmsKnowledgeService(JdbcTemplate jdbc,
                               ObjectProvider<ElasticsearchClientProvider> elasticsearchProvider,
                               ElasticsearchSearchProperties searchProperties,
                               CmsVectorIndexService vectorIndexService) {
        this.jdbc = jdbc;
        this.elasticsearchProvider = elasticsearchProvider;
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
                               COUNT(c.ordinal),d.deleted_at,d.doc_category
                        FROM cms_knowledge_document d LEFT JOIN cms_knowledge_chunk c
                          ON c.tenant_id=d.tenant_id AND c.review_id=d.review_id
                        WHERE d.tenant_id=? AND %s
                        GROUP BY d.review_id,d.title,d.validity,d.source_url,d.text_hash,d.published,d.created_at,d.deleted_at,d.doc_category
                        ORDER BY d.created_at DESC,d.review_id DESC LIMIT 200
                        """.formatted(archiveCondition);
        return jdbc.query(sql,
                (rs, row) -> new KnowledgeItem(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getBoolean(6),
                        rs.getTimestamp(7).toLocalDateTime(), rs.getInt(8),rs.getTimestamp(9)!=null,
                        rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toLocalDateTime(), rs.getString(10)), tenantId);
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
        jdbc.update("UPDATE cms_knowledge_document SET published=1,deleted_at=NULL,review_status='PUBLISHED',version=version+1 WHERE tenant_id=? AND review_id=?",
                tenantId, reviewId);
        List<IndexRecord> records = loadRecords(tenantId, reviewId);
        indexRecords(records);
        int vectorChunks = vectorIndexService.indexPublished(records.stream().map(IndexRecord::sourceId).toList());
        return new PublishResult(reviewId, true, records.size(), vectorChunks != records.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(String reviewId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        int updated = jdbc.update("UPDATE cms_knowledge_document SET published=0,deleted_at=CURRENT_TIMESTAMP,review_status='ARCHIVED',version=version+1 WHERE tenant_id=? AND review_id=? AND published=1",
                tenantId, reviewId);
        if (updated == 0) {
            return;
        }
        deleteFromElasticsearch(tenantId, sourcePrefix(tenantId, reviewId));
    }

    public void unpublish(String reviewId) { archive(reviewId); }

    /** 将归档内容恢复为已发布状态，并重新写入关键词与向量索引。 */
    @Transactional(rollbackFor = Exception.class)
    public PublishResult restore(String reviewId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cms_knowledge_document WHERE tenant_id=? AND review_id=? AND deleted_at IS NOT NULL",
                Integer.class, tenantId, reviewId);
        if (count == null || count == 0) {
            throw exception(KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        return publish(reviewId);
    }

    /**
     * 彻底删除只接受已经驳回或已进入归档库的内容，避免绕过正常下架和审查流程。
     * 子表按外键依赖顺序清理；审查任务表没有外键，也一并移除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void permanentlyDelete(String reviewId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        List<DeleteState> states = jdbc.query("""
                        SELECT JSON_UNQUOTE(JSON_EXTRACT(r.snapshot,'$.report.workflowStatus')),
                               d.review_id IS NOT NULL,d.deleted_at IS NOT NULL
                        FROM cms_document_review r
                        LEFT JOIN cms_knowledge_document d
                          ON d.tenant_id=r.tenant_id AND d.review_id=r.id
                        WHERE r.tenant_id=? AND r.id=?
                        FOR UPDATE
                        """,
                (rs, row) -> new DeleteState(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3)),
                tenantId, reviewId);
        if (states.isEmpty()) throw exception(KNOWLEDGE_DOCUMENT_NOT_FOUND);
        DeleteState state = states.getFirst();
        boolean rejected = "REJECTED".equalsIgnoreCase(state.workflowStatus());
        if (!rejected && !(state.hasKnowledgeDocument() && state.archived())) {
            throw exception(KNOWLEDGE_PERMANENT_DELETE_NOT_ALLOWED);
        }

        jdbc.update("DELETE FROM cms_law_validity_task WHERE tenant_id=? AND review_id=?", tenantId, reviewId);
        jdbc.update("DELETE FROM cms_finance_policy_ext WHERE tenant_id=? AND review_id=?", tenantId, reviewId);
        jdbc.update("DELETE FROM cms_knowledge_chunk WHERE tenant_id=? AND review_id=?", tenantId, reviewId);
        jdbc.update("DELETE FROM cms_knowledge_document WHERE tenant_id=? AND review_id=?", tenantId, reviewId);
        jdbc.update("DELETE FROM cms_review_task WHERE tenant_id=? AND review_id=?", tenantId, reviewId);
        if (jdbc.update("DELETE FROM cms_document_review WHERE tenant_id=? AND id=?", tenantId, reviewId) != 1) {
            throw exception(KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
    }

    public ReindexResult rebuildSearchIndex() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        deleteFromElasticsearch(tenantId, SOURCE_PREFIX + tenantId + ":");
        List<IndexRecord> records = loadPublishedRecords(tenantId);
        indexRecords(records);
        // 向量索引依赖外部嵌入模型；未启用 RAG 时仅重建关键词索引，法规与政策检索不受影响。
        CmsVectorIndexService.RebuildResult vector = vectorIndexService.isEmbeddingAvailable()
                ? vectorIndexService.rebuild()
                : new CmsVectorIndexService.RebuildResult(0, "", 0);
        return new ReindexResult(records.size(), vector.indexed(), vector.embeddingModel());
    }

    /** 结构化元数据变更后覆盖当前文档的 ES 内容；未发布文档仅保存 MySQL。 */
    public void refreshPublishedDocument(String reviewId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        DocumentState document = loadDocumentState(tenantId, reviewId, false);
        if (!document.published()) return;
        List<IndexRecord> records = loadRecords(tenantId, reviewId);
        indexRecords(records);
        vectorIndexService.indexPublished(records.stream().map(IndexRecord::sourceId).toList());
    }

    public List<KnowledgeChunk> listChunks(String reviewId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        requireDocument(tenantId, reviewId);
        return jdbc.query("""
                        SELECT ordinal,start_offset,end_offset,content,page_number,ai_review_result,
                               ai_review_message,is_manual_edited,version,valid
                        FROM cms_knowledge_chunk
                        WHERE tenant_id=? AND review_id=?
                        ORDER BY ordinal
                        """, (rs, row) -> new KnowledgeChunk(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                rs.getString(4), (Integer) rs.getObject(5), rs.getString(6), rs.getString(7),
                rs.getBoolean(8), rs.getLong(9), rs.getBoolean(10)), tenantId, reviewId);
    }

    /** 修改已发布切片时同步 MySQL、向量索引及 Elasticsearch，版本号用于阻止并发覆盖。 */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeChunk updateChunk(String reviewId, int ordinal, String content, long expectedVersion) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        DocumentState document = loadDocumentState(tenantId, reviewId, true);
        KnowledgeChunk before = loadChunk(tenantId, reviewId, ordinal, true);
        if (before.version() != expectedVersion) throw exception(KNOWLEDGE_CHUNK_VERSION_CONFLICT);
        String normalized = requireChunkContent(content);
        long nextVersion = before.version() + 1;
        int updated = jdbc.update("""
                        UPDATE cms_knowledge_chunk
                        SET content=?,is_manual_edited=1,version=?,ai_review_result='PASS',
                            ai_review_message='人工修正后通过'
                        WHERE tenant_id=? AND review_id=? AND ordinal=? AND version=?
                        """, normalized, nextVersion, tenantId, reviewId, ordinal, expectedVersion);
        if (updated != 1) throw exception(KNOWLEDGE_CHUNK_VERSION_CONFLICT);
        if (document.published()) {
            syncPublishedChunk(tenantId, reviewId, ordinal, normalized, nextVersion, document.category(),
                    before.content(), before.version());
        }
        return loadChunk(tenantId, reviewId, ordinal, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeChunk addChunk(String reviewId, String content, Integer pageNumber) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        DocumentState document = loadDocumentState(tenantId, reviewId, true);
        String normalized = requireChunkContent(content);
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(ordinal),0) FROM cms_knowledge_chunk WHERE tenant_id=? AND review_id=?",
                Integer.class, tenantId, reviewId);
        int ordinal = (max == null ? 0 : max) + 1;
        jdbc.update("""
                        INSERT INTO cms_knowledge_chunk
                        (review_id,tenant_id,ordinal,start_offset,end_offset,content,page_number,
                         ai_review_result,ai_review_message,is_manual_edited,version,valid)
                        VALUES (?,?,?,?,?,?,?,'PASS','人工补充切片',1,0,1)
                        """, reviewId, tenantId, ordinal, 0, normalized.length(), normalized, pageNumber);
        if (document.published()) {
            syncPublishedChunk(tenantId, reviewId, ordinal, normalized, 0, document.category(), null, -1);
        }
        return loadChunk(tenantId, reviewId, ordinal, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteChunk(String reviewId, int ordinal, long expectedVersion) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        DocumentState document = loadDocumentState(tenantId, reviewId, true);
        KnowledgeChunk before = loadChunk(tenantId, reviewId, ordinal, true);
        if (before.version() != expectedVersion) throw exception(KNOWLEDGE_CHUNK_VERSION_CONFLICT);
        String sourceId = sourceId(tenantId, reviewId, ordinal);
        if (document.published()) {
            try {
                deleteSourceFromElasticsearch(tenantId, sourceId);
                vectorIndexService.deletePublishedText(tenantId, sourceId);
            } catch (RuntimeException ex) {
                throw exception(KNOWLEDGE_CHUNK_SYNC_FAILED);
            }
        }
        int deleted = jdbc.update("DELETE FROM cms_knowledge_chunk WHERE tenant_id=? AND review_id=? AND ordinal=? AND version=?",
                tenantId, reviewId, ordinal, expectedVersion);
        if (deleted != 1) throw exception(KNOWLEDGE_CHUNK_VERSION_CONFLICT);
    }

    private void syncPublishedChunk(long tenantId, String reviewId, int ordinal, String content, long version,
                                    String category,
                                    String rollbackContent, long rollbackVersion) {
        String sourceId = sourceId(tenantId, reviewId, ordinal);
        IndexRecord record = loadRecords(tenantId, reviewId).stream()
                .filter(item -> item.sourceId().equals(sourceId)).findFirst()
                .orElseThrow(() -> exception(KNOWLEDGE_CHUNK_NOT_FOUND));
        try {
            indexRecords(List.of(record));
            if (isVectorCategory(category)) {
                vectorIndexService.upsertPublishedText(tenantId, sourceId, chunkVersion(version), content, category);
            }
        } catch (RuntimeException ex) {
            if (rollbackContent != null && rollbackVersion >= 0) {
                try {
                    Map<String, Object> rollbackDocument = new LinkedHashMap<>(record.document());
                    rollbackDocument.put("content", rollbackContent);
                    rollbackDocument.put("revision", rollbackVersion);
                    indexRecords(List.of(new IndexRecord(sourceId, record.category(), rollbackDocument)));
                    if (isVectorCategory(category)) {
                        vectorIndexService.upsertPublishedText(tenantId, sourceId,
                                chunkVersion(rollbackVersion), rollbackContent, category);
                    }
                } catch (RuntimeException compensationFailure) {
                    log.error("[syncPublishedChunk][索引补偿失败 sourceId={}]", sourceId, compensationFailure);
                }
            } else {
                deleteSourceFromElasticsearch(tenantId, sourceId);
                vectorIndexService.deletePublishedText(tenantId, sourceId);
            }
            log.warn("[syncPublishedChunk][切片同步失败 sourceId={}]", sourceId, ex);
            throw exception(KNOWLEDGE_CHUNK_SYNC_FAILED);
        }
    }

    private void requireDocument(long tenantId, String reviewId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cms_knowledge_document WHERE tenant_id=? AND review_id=?",
                Integer.class, tenantId, reviewId);
        if (count == null || count == 0) throw exception(KNOWLEDGE_DOCUMENT_NOT_FOUND);
    }

    private DocumentState loadDocumentState(long tenantId, String reviewId, boolean lock) {
        List<DocumentState> states = jdbc.query("SELECT published,doc_category FROM cms_knowledge_document WHERE tenant_id=? AND review_id=?"
                        + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new DocumentState(rs.getBoolean(1), rs.getString(2)), tenantId, reviewId);
        if (states.isEmpty()) throw exception(KNOWLEDGE_DOCUMENT_NOT_FOUND);
        return states.getFirst();
    }

    private KnowledgeChunk loadChunk(long tenantId, String reviewId, int ordinal, boolean lock) {
        List<KnowledgeChunk> chunks = jdbc.query("""
                        SELECT ordinal,start_offset,end_offset,content,page_number,ai_review_result,
                               ai_review_message,is_manual_edited,version,valid
                        FROM cms_knowledge_chunk WHERE tenant_id=? AND review_id=? AND ordinal=?
                        """ + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new KnowledgeChunk(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4),
                        (Integer) rs.getObject(5), rs.getString(6), rs.getString(7), rs.getBoolean(8),
                        rs.getLong(9), rs.getBoolean(10)), tenantId, reviewId, ordinal);
        if (chunks.isEmpty()) throw exception(KNOWLEDGE_CHUNK_NOT_FOUND);
        return chunks.getFirst();
    }

    private static String requireChunkContent(String content) {
        String value = content == null ? "" : content.strip();
        if (value.isEmpty() || value.length() > 100_000) throw exception(KNOWLEDGE_CHUNK_NOT_FOUND);
        return value;
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
        Map<String, FinancePolicyIndexMetadata> financeMetadata = loadFinancePolicyMetadata(tenantId, reviewIdFilter);
        String sql = """
                        SELECT d.review_id,d.text_hash,c.ordinal,c.content,c.version,
                               d.local_pdf_path,c.page_number,d.doc_category
                        FROM cms_knowledge_document d JOIN cms_knowledge_chunk c
                          ON c.tenant_id=d.tenant_id AND c.review_id=d.review_id
                        WHERE d.tenant_id=? AND d.published=1 AND c.valid=1
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
            document.put("review_id", reviewId);
            String docCategory = rs.getString(8);
            document.put("doc_category", docCategory == null || docCategory.isBlank() ? "LAW" : docCategory);
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
            String localOriginalPath = rs.getString(6);
            document.put("has_local_original", localOriginalPath != null && !localOriginalPath.isBlank());
            if (localOriginalPath != null && !localOriginalPath.isBlank()) document.put("pdf_url", localOriginalPath);
            Integer pageNumber = (Integer) rs.getObject(7);
            if (pageNumber != null) document.put("page_number", pageNumber);
            if (metadata.effectiveDate() != null) document.put("effective_date", metadata.effectiveDate().toString());
            if (metadata.expiryDate() != null) document.put("expiry_date", metadata.expiryDate().toString());
            FinancePolicyIndexMetadata policy = financeMetadata.get(reviewId);
            if (policy != null && "FINANCE_POLICY".equalsIgnoreCase(docCategory)) {
                putIfPresent(document, "applicable_regions", policy.regions());
                putIfPresent(document, "applicable_industries", policy.industries());
                putIfPresent(document, "applicable_enterprise_types", policy.enterpriseTypes());
                putIfPresent(document, "support_type", policy.supportType());
                putIfPresent(document, "amount_min", policy.amountMin());
                putIfPresent(document, "amount_max", policy.amountMax());
                putIfPresent(document, "support_rate", policy.supportRate());
                putIfPresent(document, "apply_start_time", policy.applyStartTime());
                putIfPresent(document, "apply_end_time", policy.applyEndTime());
                document.put("policy_status", policy.policyStatus());
            }
            document.put("published", true);
            document.put("revision", rs.getLong(5));
            return new IndexRecord(sourceId, String.valueOf(document.get("doc_category")), document);
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
                                .index(writeAlias(record.category())).id(record.sourceId()).document(record.document())));
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
            requiredClient().deleteByQuery(builder -> builder.index(readAliases()).query(query).refresh(true));
        } catch (IOException ex) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    private Map<String, FinancePolicyIndexMetadata> loadFinancePolicyMetadata(long tenantId, String reviewId) {
        String sql = """
                SELECT review_id,applicable_regions,applicable_industries,applicable_enterprise_types,
                       support_type,amount_min,amount_max,support_rate,apply_start_time,apply_end_time,policy_status
                FROM cms_finance_policy_ext WHERE tenant_id=? AND deleted=0
                """ + (reviewId == null ? "" : " AND review_id=?");
        Object[] args = reviewId == null ? new Object[]{tenantId} : new Object[]{tenantId, reviewId};
        try {
            return jdbc.query(sql, (rs, row) -> new FinancePolicyIndexMetadata(
                            rs.getString(1), JsonUtils.parseArray(rs.getString(2), String.class),
                            JsonUtils.parseArray(rs.getString(3), String.class),
                            JsonUtils.parseArray(rs.getString(4), String.class), rs.getString(5),
                            rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8),
                            rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toLocalDateTime().toString(),
                            rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toLocalDateTime().toString(),
                            rs.getInt(11)), args).stream().collect(java.util.stream.Collectors.toMap(
                            FinancePolicyIndexMetadata::reviewId, value -> value));
        } catch (DataAccessException ex) {
            // 允许应用先升级、迁移脚本稍后执行；金融政策接口本身仍会明确暴露缺表错误。
            log.warn("[loadFinancePolicyMetadata][金融政策扩展表尚不可用，跳过结构化字段]", ex);
            return Map.of();
        }
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) return;
        if (value instanceof List<?> list && list.isEmpty()) return;
        if (value instanceof String text && text.isBlank()) return;
        target.put(key, value);
    }

    private void deleteSourceFromElasticsearch(long tenantId, String sourceId) {
        Query query = Query.of(builder -> builder.bool(bool -> bool
                .filter(filter -> filter.term(term -> term.field("tenant_id").value(String.valueOf(tenantId))))
                .filter(filter -> filter.term(term -> term.field("source_id").value(sourceId)))));
        try {
            requiredClient().deleteByQuery(builder -> builder.index(readAliases()).query(query).refresh(true));
        } catch (IOException ex) {
            throw exception(LAW_SEARCH_UNAVAILABLE);
        }
    }

    private ElasticsearchClient requiredClient() {
        ElasticsearchClientProvider provider = elasticsearchProvider.getIfAvailable();
        if (provider == null) throw exception(LAW_SEARCH_UNAVAILABLE);
        return provider.client();
    }

    private List<String> readAliases() {
        return List.of(searchProperties.getIndexPrefix() + "-law-read",
                searchProperties.getIndexPrefix() + "-qa-read",
                searchProperties.getIndexPrefix() + "-finance-policy-read");
    }
    private String writeAlias(String category) {
        if ("QA".equalsIgnoreCase(category)) return searchProperties.getIndexPrefix() + "-qa-write";
        if ("FINANCE_POLICY".equalsIgnoreCase(category)) {
            return searchProperties.getIndexPrefix() + "-finance-policy-write";
        }
        return searchProperties.getIndexPrefix() + "-law-write";
    }
    private static boolean isVectorCategory(String category) {
        return "QA".equalsIgnoreCase(category) || "FINANCE_POLICY".equalsIgnoreCase(category);
    }
    private static String sourcePrefix(long tenantId, String reviewId) { return SOURCE_PREFIX + tenantId + ":" + reviewId + ":"; }
    private static String sourceId(long tenantId, String reviewId, int ordinal) { return sourcePrefix(tenantId, reviewId) + ordinal; }
    private static String chunkVersion(long version) { return "chunk-" + version; }
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

    private record IndexRecord(String sourceId, String category, Map<String, Object> document) { }
    private record DocumentState(boolean published, String category) { }
    private record DeleteState(String workflowStatus, boolean hasKnowledgeDocument, boolean archived) { }
    private record FinancePolicyIndexMetadata(String reviewId, List<String> regions, List<String> industries,
                                              List<String> enterpriseTypes, String supportType,
                                              BigDecimal amountMin, BigDecimal amountMax, BigDecimal supportRate,
                                              String applyStartTime, String applyEndTime, int policyStatus) { }
    public record KnowledgeItem(String reviewId, String title, String validity, String sourceUrl,
                                String textHash, boolean published, LocalDateTime createdAt, int chunkCount,
                                boolean archived, LocalDateTime archivedAt, String docCategory) { }
    public record PublishResult(String reviewId, boolean published, int keywordChunks,
                                boolean vectorRebuildRequired) { }
    public record ReindexResult(int keywordChunks, int vectorChunks, String embeddingModel) { }
    public record KnowledgeChunk(int ordinal, int startOffset, int endOffset, String content,
                                 Integer pageNumber, String aiReviewResult, String aiReviewMessage,
                                 boolean manualEdited, long version, boolean valid) { }

}
