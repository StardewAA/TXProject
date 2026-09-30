package cn.iocoder.txgy.module.bidding.service.bidagent.index;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentChunkDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentChunkMapper;
import cn.iocoder.txgy.module.bidding.framework.bidagent.BidAgentProperties;
import cn.iocoder.txgy.module.bidding.service.bidagent.retrieval.BidRetrievalServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_DOC_DOWNLOAD_FAIL;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_DOC_EMPTY;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_DOC_READ_FAIL;

/**
 * 索引服务实现：切分 → 向量化 → 写向量库（含溯源 metadata）→ 落片段表。
 */
@Service
@Slf4j
public class BidIndexServiceImpl implements BidIndexService {

    /** 切分窗口（字符）默认值：可用 bidding.bid-agent.index.chunk-size 覆盖 */
    private static final int CHUNK_SIZE = 500;
    /** 相邻片段重叠字符数默认值（窗口 10%）：可用 bidding.bid-agent.index.chunk-overlap 覆盖 */
    private static final int CHUNK_OVERLAP = 50;

    @Resource
    private BidAgentChunkMapper chunkMapper;

    private final VectorStore vectorStore;
    private final BidAgentProperties properties;

    public BidIndexServiceImpl(ObjectProvider<VectorStore> vectorStoreProvider, BidAgentProperties properties) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.properties = properties;
    }

    @Override
    public int indexDocument(Long tenantId, Long projectId, Long materialId, String docType, String sourceDocName,
                             String bidSectionCode, String content, Map<String, Object> extraMeta) {
        if (StrUtil.isBlank(content)) {
            return 0;
        }
        if (!isIndexEnabled()) {
            log.warn("[index][向量库不可用，跳过入库] sourceDocName={}", sourceDocName);
            return 0;
        }
        // 入库前清洗：去噪（页码/页眉页脚/水印/装饰行）→ 结构归一（合硬换行、去重复段）→ 编码统一（全角/零宽/换行）
        String cleaned = cleanContent(content, sourceDocName);
        if (StrUtil.isBlank(cleaned)) {
            log.warn("[index][清洗后无有效文本，跳过入库] sourceDocName={}, rawChars={}", sourceDocName, content.length());
            return 0;
        }
        return TenantUtils.execute(tenantId, () -> {
            // 先删后建，保证文档更新幂等；材料侧按材料编号精确清理，契约端按文档名清理
            if (materialId != null) {
                deleteSegmentsByMaterialId(materialId);
            } else {
                deleteSegmentsBySourceDocName(sourceDocName);
            }

int chunkSize = chunkSize();
int chunkOverlap = chunkOverlap(chunkSize);
List<String> chunks = splitContent(cleaned, chunkSize, chunkOverlap);
List<Document> docs = new ArrayList<>();
List<BidAgentChunkDO> rows = new ArrayList<>();
            int index = 0;
            for (String chunk : chunks) {
                index++;
                String blockId = sourceDocName + "#" + index;
                Document doc = new Document(chunk);
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_TENANT_ID, tenantId.toString());
                if (projectId != null) {
                    doc.getMetadata().put(BidRetrievalServiceImpl.MD_PROJECT_ID, projectId.toString());
                }
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_DOC_TYPE, docType);
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_SOURCE_DOC, sourceDocName);
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_BLOCK_IDS, blockId);
                if (StrUtil.isNotBlank(bidSectionCode)) {
                    doc.getMetadata().put(BidRetrievalServiceImpl.MD_SECTION, bidSectionCode);
                }
                if (extraMeta != null) {
                    doc.getMetadata().putAll(extraMeta);
                }
                docs.add(doc);

                BidAgentChunkDO segment = new BidAgentChunkDO();
                segment.setTenantId(tenantId);
                segment.setProjectId(projectId);
                segment.setMaterialId(materialId);
                segment.setDocType(docType);
                segment.setSourceDocName(sourceDocName);
                segment.setBidSectionCode(bidSectionCode);
                segment.setChapterPath(asString(extraMeta == null ? null : extraMeta.get("chapterPath")));
                segment.setPageNo(asInteger(extraMeta == null ? null : extraMeta.get("pageNo")));
                segment.setSourceBlockIds(blockId);
                segment.setContent(chunk);
                rows.add(segment);
            }
            // 批量写入：一次向量化/upsert 批处理 + 一次批量 INSERT，避免每 chunk 一次远程往返 + 一次 DB 写入
            if (!docs.isEmpty()) {
                vectorStore.add(docs);
                for (int i = 0; i < rows.size(); i++) {
                    rows.get(i).setVectorId(docs.get(i).getId());
                }
                chunkMapper.insertBatch(rows);
            }
            log.info("[index][入库完成] sourceDocName={}, chunks={}, chunkSize={}, chunkOverlap={}",
                    sourceDocName, chunks.size(), chunkSize, chunkOverlap);
            return chunks.size();
        });
    }

    @Override
    public int indexUrl(Long tenantId, Long projectId, Long materialId, String docType, String sourceDocName,
                        String bidSectionCode, String url, Map<String, Object> extraMeta) {
        // 先判开关再下载：检索未启用时不该产生一次无意义的外网下载（也避免把"未启用"误报成"下载失败"）
        if (!isIndexEnabled()) {
            log.warn("[index][向量库不可用，跳过入库] sourceDocName={} url={}", sourceDocName, url);
            return 0;
        }
        String content = readUrl(url);
        return indexDocument(tenantId, projectId, materialId, docType, sourceDocName, bidSectionCode, content, extraMeta);
    }

    /** 检索能力是否可用：ai-enabled=true 且向量库 Bean 已就绪 */
    private boolean isIndexEnabled() {
        return Boolean.TRUE.equals(properties.getAiEnabled()) && vectorStore != null;
    }

    /**
     * 入库文本清洗：索引阶段在这里一次性完成去噪、结构归一与编码统一（{@link BidTextCleaner}）。
     *
     * <p>为什么必须清洗：Tika 抽出的全文带页码、页眉页脚、水印、硬换行与全角/零宽混排，
     * 直接切片会让同一份资料在不同来源下产出不同片段，召回不可复算。清洗只删非正文行
     * （页码/页眉页脚/水印/装饰行）与相邻重复段，不改写正文用词；每次入库都把统计写进日志，
     * 便于专员复核"这份资料到底被改了什么"。
     *
     * <p>为什么保留开关：{@code bidding.bid-agent.index.clean-enabled=false} 时原文直入，
     * 用于回滚与 A/B 对比（与检索侧 bm25-enabled 同一口径：只做开关，不做静默降级）。
     */
    private String cleanContent(String content, String sourceDocName) {
        BidAgentProperties.Index index = properties.getIndex();
        if (index != null && !Boolean.TRUE.equals(index.getCleanEnabled())) {
            log.info("[index][文本清洗已关闭，原文入库] sourceDocName={}, chars={}", sourceDocName, content.length());
            return content;
        }
        BidTextCleaner.Result cleaned = BidTextCleaner.clean(content, sourceDocName);
        log.info("[index][文本清洗] sourceDocName={}, {}", sourceDocName, cleaned.summary());
        if (log.isDebugEnabled() && !cleaned.getDroppedSamples().isEmpty()) {
            log.debug("[index][文本清洗-删除抽样] sourceDocName={}, samples={}", sourceDocName,
                    cleaned.getDroppedSamples());
        }
        return cleaned.getText();
    }

    @Override
    public Map<String, Object> pageSegments(Long tenantId, String sourceDocName, Integer pageNo, Integer pageSize) {
        return TenantUtils.execute(tenantId, () -> {
            PageParam pageParam = new PageParam();
            pageParam.setPageNo(pageNo == null || pageNo <= 0 ? 1 : pageNo);
            pageParam.setPageSize(pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200));
            PageResult<BidAgentChunkDO> result = chunkMapper.selectPageBySourceDocName(pageParam, sourceDocName);
            return toSegmentPage(result, pageParam);
        });
    }

    @Override
    public Map<String, Object> pageSegmentsByMaterialId(Long tenantId, Long materialId, Integer pageNo, Integer pageSize) {
        return TenantUtils.execute(tenantId, () -> {
            PageParam pageParam = new PageParam();
            pageParam.setPageNo(pageNo == null || pageNo <= 0 ? 1 : pageNo);
            pageParam.setPageSize(pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 200));
            PageResult<BidAgentChunkDO> result = chunkMapper.selectPageByMaterialId(pageParam, materialId);
            return toSegmentPage(result, pageParam);
        });
    }

    private Map<String, Object> toSegmentPage(PageResult<BidAgentChunkDO> result, PageParam pageParam) {
        List<Map<String, Object>> items = new ArrayList<>();
        int chunkNo = (pageParam.getPageNo() - 1) * pageParam.getPageSize();
        for (BidAgentChunkDO segment : result.getList()) {
            chunkNo++;
            Map<String, Object> item = new HashMap<>();
            item.put("id", segment.getId());
            item.put("chunkNo", chunkNo);
            item.put("content", segment.getContent());
            item.put("sourceBlockIds", segment.getSourceBlockIds());
            item.put("pageNo", segment.getPageNo());
            item.put("chapterPath", segment.getChapterPath());
            item.put("bidSectionCode", segment.getBidSectionCode());
            item.put("edited", segment.getEdited() == null ? 0 : segment.getEdited());
            items.add(item);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", items);
        data.put("total", result.getTotal());
        return data;
    }

    /**
     * 下载并解析远程文档为全文（复用 ai 模块同款 TikaDocumentReader 用法，编译已验证）
     */
    private String readUrl(String url) {
        // 容器化部署：文件 url 的 host 是宿主的 127.0.0.1:48080，容器内访问不到，重写为网关容器名
        if (StrUtil.isNotBlank(properties.getFileDownloadHost())) {
            url = url.replaceFirst("://[^/]+", "://" + properties.getFileDownloadHost());
        }
        byte[] bytes;
        try {
            bytes = HttpUtil.downloadBytes(url);
        } catch (Exception ex) {
            log.error("[readUrl][url({}) 下载失败]", url, ex);
            throw exception(BID_DOC_DOWNLOAD_FAIL);
        }
        if (bytes == null || bytes.length == 0) {
            log.error("[readUrl][url({}) 内容为空]", url);
            throw exception(BID_DOC_EMPTY);
        }
        List<Document> documents;
        try {
            TikaDocumentReader loader = new TikaDocumentReader(new ByteArrayResource(bytes));
            documents = loader.get();
        } catch (Exception ex) {
            log.error("[readUrl][url({}) 解析失败]", url, ex);
            throw exception(BID_DOC_READ_FAIL);
        }
        Document document = CollUtil.getFirst(documents);
        if (document == null || StrUtil.isEmpty(document.getText())) {
            log.error("[readUrl][url({}) 解析结果为空]", url);
            throw exception(BID_DOC_READ_FAIL);
        }
        return document.getText();
    }

    @Override
    public void deleteBySourceDocName(Long tenantId, String sourceDocName) {
        if (tenantId == null) {
            return;
        }
        TenantUtils.execute(tenantId, () -> {
            deleteSegmentsBySourceDocName(sourceDocName);
            return null;
        });
    }

    @Override
    public void deleteByMaterialId(Long tenantId, Long materialId) {
        if (tenantId == null || materialId == null) {
            return;
        }
        TenantUtils.execute(tenantId, () -> {
            deleteSegmentsByMaterialId(materialId);
            return null;
        });
    }

    @Override
    public void reindexChunk(Long tenantId, BidAgentChunkDO chunk) {
        if (tenantId == null || chunk == null) {
            return;
        }
        TenantUtils.execute(tenantId, () -> {
            // 向量库不可用（ai-enabled=false 或无 Bean）时静默跳过：台账已由调用方更新，向量留待后续重建
            if (vectorStore == null || !Boolean.TRUE.equals(properties.getAiEnabled())) {
                log.warn("[index][向量库不可用，跳过单切片重向量化] chunkId={}", chunk.getId());
                return null;
            }
            String oldVectorId = chunk.getVectorId();
            Document doc = new Document(chunk.getContent());
            doc.getMetadata().put(BidRetrievalServiceImpl.MD_TENANT_ID, tenantId.toString());
            if (chunk.getProjectId() != null) {
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_PROJECT_ID, chunk.getProjectId().toString());
            }
            if (StrUtil.isNotBlank(chunk.getDocType())) {
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_DOC_TYPE, chunk.getDocType());
            }
            if (StrUtil.isNotBlank(chunk.getSourceDocName())) {
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_SOURCE_DOC, chunk.getSourceDocName());
            }
            if (StrUtil.isNotBlank(chunk.getBidSectionCode())) {
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_SECTION, chunk.getBidSectionCode());
            }
            if (StrUtil.isNotBlank(chunk.getChapterPath())) {
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_CHAPTER, chunk.getChapterPath());
            }
            if (chunk.getPageNo() != null) {
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_PAGE, chunk.getPageNo());
            }
            if (StrUtil.isNotBlank(chunk.getSourceBlockIds())) {
                doc.getMetadata().put(BidRetrievalServiceImpl.MD_BLOCK_IDS, chunk.getSourceBlockIds());
            }
            vectorStore.add(List.of(doc));
            chunk.setVectorId(doc.getId());
            chunkMapper.updateById(chunk);
            // 先写新向量、台账落库后再删旧向量：add/update 失败时旧向量仍在，
            // 避免「先删后写」在 add 失败时导致该切片向量永久丢失（内容改了但检索不到）
            if (StrUtil.isNotBlank(oldVectorId) && !oldVectorId.equals(chunk.getVectorId())) {
                vectorStore.delete(List.of(oldVectorId));
            }
            return null;
        });
    }

    @Override
    public void deleteChunks(Long tenantId, List<BidAgentChunkDO> chunks) {
        if (tenantId == null || CollUtil.isEmpty(chunks)) {
            return;
        }
        TenantUtils.execute(tenantId, () -> {
            if (vectorStore != null) {
                List<String> vectorIds = chunks.stream()
                        .map(BidAgentChunkDO::getVectorId)
                        .filter(StrUtil::isNotBlank)
                        .toList();
                if (CollUtil.isNotEmpty(vectorIds)) {
                    vectorStore.delete(vectorIds);
                }
            }
            chunkMapper.deleteBatchIds(chunks.stream().map(BidAgentChunkDO::getId).toList());
            return null;
        });
    }

    private void deleteSegmentsBySourceDocName(String sourceDocName) {
        List<BidAgentChunkDO> existing = chunkMapper.selectListBySourceDocName(sourceDocName);
        deleteSegments(existing);
    }

    private void deleteSegmentsByMaterialId(Long materialId) {
        List<BidAgentChunkDO> existing = chunkMapper.selectListByMaterialId(materialId);
        deleteSegments(existing);
    }

    private void deleteSegments(List<BidAgentChunkDO> existing) {
        if (CollUtil.isEmpty(existing)) {
            return;
        }
        if (vectorStore != null) {
            List<String> vectorIds = new ArrayList<>();
            for (BidAgentChunkDO segment : existing) {
                if (StrUtil.isNotBlank(segment.getVectorId())) {
                    vectorIds.add(segment.getVectorId());
                }
            }
            if (CollUtil.isNotEmpty(vectorIds)) {
                vectorStore.delete(vectorIds);
            }
        }
        if (CollUtil.isNotEmpty(existing)) {
            chunkMapper.deleteBatchIds(existing.stream().map(BidAgentChunkDO::getId).toList());
        }
    }

    /** 切分窗口：优先取配置，非法值回退默认（默认 500 字符） */
    private int chunkSize() {
        BidAgentProperties.Index index = properties.getIndex();
        Integer size = index == null ? null : index.getChunkSize();
        return size == null || size <= 0 ? CHUNK_SIZE : size;
    }

    /**
     * 相邻片段重叠字符数：优先取配置（默认 50），并限制不超过窗口一半。
     *
     * <p>限制的原因：重叠一旦接近窗口长度，每次切分都会把上一片段几乎整段带入下一片段，
     * 片段数会翻倍且内容高度重复，得不偿失。
     */
    private int chunkOverlap(int chunkSize) {
        BidAgentProperties.Index index = properties.getIndex();
        Integer overlap = index == null ? null : index.getChunkOverlap();
        int value = overlap == null ? CHUNK_OVERLAP : overlap;
        if (value <= 0) {
            return 0;
        }
        return Math.min(value, Math.max(1, chunkSize / 2));
    }

    /**
     * 按段落贪心切分，并让相邻片段保留 {@code overlapChars} 重叠（默认窗口的 10%）。
     *
     * <p>为什么要重叠：答案经常落在切分点两侧——一条合同条款被切成两段时，两个片段各持半句，
     * 向量与关键词都可能只命中半截；重叠保证跨边界的内容至少在一个片段里是完整的。
     *
     * <p>为什么按段落而不按字符硬切：清洗阶段已经把硬换行合并成完整段落，
     * 以段落为单位切分不会把一句话从中间截断（超长单段仍会整段成片，与切分前行为一致）。
     */
    static List<String> splitContent(String content, int maxChars, int overlapChars) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\\n+");
        StringBuilder sb = new StringBuilder();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 单段落超长时强制硬切分：无换行的长文本（常见于 PDF 解析）不再生成远超窗口的超大 chunk
            if (trimmed.length() > maxChars) {
                if (sb.length() > 0) {
                    chunks.add(sb.toString());
                    sb.setLength(0);
                }
                for (int start = 0; start < trimmed.length(); start += maxChars) {
                    chunks.add(trimmed.substring(start, Math.min(trimmed.length(), start + maxChars)));
                }
                continue;
            }
            if (sb.length() > 0 && sb.length() + trimmed.length() + 1 > maxChars) {
                String chunk = sb.toString();
                chunks.add(chunk);
                sb.setLength(0);
                if (overlapChars > 0) {
                    sb.append(tailOf(chunk, overlapChars));
                }
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(trimmed);
        }
        if (sb.length() > 0) {
            chunks.add(sb.toString());
        }
        return chunks;
    }

    /** 取片段尾部作为下一片段的重叠前缀；从首个非空白字符起头，避免片段以半截空白开头 */
    private static String tailOf(String chunk, int overlapChars) {
        if (chunk.length() <= overlapChars) {
            return chunk;
        }
        String tail = chunk.substring(chunk.length() - overlapChars);
        int start = 0;
        while (start < tail.length() && Character.isWhitespace(tail.charAt(start))) {
            start++;
        }
        return tail.substring(start);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
