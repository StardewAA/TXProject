package cn.iocoder.txgy.module.bidding.service.bidagent.index;

import java.util.Map;
import java.util.List;

import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentChunkDO;

/**
 * 投标检索索引服务：把招标文件/企业资料切分、向量化并写入向量库 + 片段表（检索的前置链路）。
 */
public interface BidIndexService {

    /**
     * 索引一份文档
     *
     * @param tenantId      租户编号
     * @param projectId     项目编号（企业资料可为空）
     * @param materialId    材料编号（材料侧入库必传，切片按材料精确级联；契约端按名入库传 null）
     * @param docType       文档类型：TENDER/FACT/TEMPLATE
     * @param sourceDocName 来源文档名
     * @param bidSectionCode 标段编号（TENDER 必填，检索按标段隔离；FACT/TEMPLATE 可空）
     * @param content       文档全文
     * @param extraMeta     额外元数据（chapterPath/pageNo 等）
     * @return 写入的片段数
     */
    int indexDocument(Long tenantId, Long projectId, Long materialId, String docType, String sourceDocName, String bidSectionCode,
                      String content, Map<String, Object> extraMeta);

    /**
     * 解析并索引一份远程文档（PDF/docx 等，经 Tika 抽取全文后走 {@link #indexDocument}）
     *
     * @param url 文档下载地址（http/https，需可达）
     */
    int indexUrl(Long tenantId, Long projectId, Long materialId, String docType, String sourceDocName, String bidSectionCode,
                 String url, Map<String, Object> extraMeta);

    /**
     * 删除某文档的全部片段（契约端文档更新时先删后建，按来源文档名匹配）
     */
    void deleteBySourceDocName(Long tenantId, String sourceDocName);

    /**
     * 按材料编号删除全部片段与向量（材料删除/重建索引用，精确级联，不受同名文件影响）
     */
    void deleteByMaterialId(Long tenantId, Long materialId);

    /**
     * 按来源文档名分页查询切片台账。
     *
     * <p>契约端 {@code /contract/segment/page} 使用本方法；招投标侧「知识库管理 → 切片与内容」
     * 用 {@link #pageSegmentsByMaterialId} 按材料编号精确分页。
     */
    Map<String, Object> pageSegments(Long tenantId, String sourceDocName, Integer pageNo, Integer pageSize);

    /**
     * 按材料编号分页查询切片台账（招投标侧「切片与内容」用，避免同名文件混页）。
     */
    Map<String, Object> pageSegmentsByMaterialId(Long tenantId, Long materialId, Integer pageNo, Integer pageSize);

    /**
     * 重新向量化单个切片（切片管理「编辑/拆分/合并」后同步向量库）：
     * 删除旧向量 → 对新正文 embedding → 写回向量库 → 更新该切片行的 vector_id。
     *
     * <p>向量库不可用（ai-enabled=false 或无 vectorStore Bean）时静默跳过，仅由调用方更新台账，
     * 保证「人工改切片」在无云端环境下也能落台账，避免编辑动作被向量库故障阻断。
     */
    void reindexChunk(Long tenantId, BidAgentChunkDO chunk);

    /**
     * 批量删除切片（切片管理「批量删除」）：先删向量库对应点，再删切片台账行。
     */
    void deleteChunks(Long tenantId, List<BidAgentChunkDO> chunks);
}
