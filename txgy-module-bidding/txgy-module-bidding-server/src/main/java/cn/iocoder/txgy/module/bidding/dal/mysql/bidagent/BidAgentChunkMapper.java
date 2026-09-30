package cn.iocoder.txgy.module.bidding.dal.mysql.bidagent;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentChunkDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 检索片段 Mapper
 */
@Mapper
public interface BidAgentChunkMapper extends BaseMapperX<BidAgentChunkDO> {

    default List<BidAgentChunkDO> selectListBySourceDocName(String sourceDocName) {
        return selectList(new LambdaQueryWrapperX<BidAgentChunkDO>()
                .eq(BidAgentChunkDO::getSourceDocName, sourceDocName));
    }

    /** 按材料编号查全部切片（材料删除/重建索引用，精确级联） */
    default List<BidAgentChunkDO> selectListByMaterialId(Long materialId) {
        return selectList(new LambdaQueryWrapperX<BidAgentChunkDO>()
                .eq(BidAgentChunkDO::getMaterialId, materialId)
                .orderByAsc(BidAgentChunkDO::getId));
    }

    /** 按材料编号分页（招投标侧「切片与内容」只读台账） */
    default PageResult<BidAgentChunkDO> selectPageByMaterialId(PageParam pageParam, Long materialId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<BidAgentChunkDO>()
                .eq(BidAgentChunkDO::getMaterialId, materialId)
                .orderByAsc(BidAgentChunkDO::getId));
    }

    /** 按主键批量查询（切片管理「编辑/拆分/合并/删除」共用），保持入参顺序返回不保证，调用方自行排序 */
    default List<BidAgentChunkDO> selectListByIds(List<Long> ids) {
        return selectList(new LambdaQueryWrapperX<BidAgentChunkDO>()
                .in(BidAgentChunkDO::getId, ids));
    }

    /** 按来源文档名分页（招投标侧「切片与内容」只读台账） */
    default PageResult<BidAgentChunkDO> selectPageBySourceDocName(PageParam pageParam, String sourceDocName) {
        return selectPage(pageParam, new LambdaQueryWrapperX<BidAgentChunkDO>()
                .eq(BidAgentChunkDO::getSourceDocName, sourceDocName)
                .orderByAsc(BidAgentChunkDO::getId));
    }

    /**
     * 关键词通道粗召回（BM25 / MySQL FULLTEXT + ngram）。
     *
     * <p>用 NATURAL LANGUAGE MODE 而不是 BOOLEAN MODE：用户 query 里出现的 `+` / `-` / `*` 在布尔模式下会被当成
     * 操作符，改变语义甚至报错；自然语言模式只做分词 + 相关度排序，配合下游 RRF + rerank 已经够用。
     *
     * <p>过滤维度与向量检索侧的应用层过滤（{@code filterByScope}）保持同一套口径：
     * 租户由 TenantBaseDO 的租户拦截器自动拼（契约端在 TenantUtils.execute 内执行），
     * 其余项目/文档类型/标段/来源文档在这里显式过滤，避免关键词通道漏掉隔离条件。
     * 参数一律 #{} 绑定，禁止拼接 SQL 文本。
     *
     * <p>索引由 sql/history/2026-09-28-bid-agent-bm25.sql 创建（content 列 FULLTEXT WITH PARSER ngram）；
     * 索引缺失时该 SQL 会抛异常，调用方（BidRetrievalServiceImpl.searchByKeyword）降级为"无关键词候选"。
     *
     * @param keyword        检索词（自然语言模式，不必自己加操作符）
     * @param projectId      项目编号，空=不限
     * @param docType        文档类型（TENDER/FACT/TEMPLATE），空=不限
     * @param bidSectionCodes 标段编号列表（多标段一次检索），空=不限
     * @param sectionIncludeUnbound 标段过滤时是否放行未绑标段的材料（FACT/TEMPLATE 跨标段复用）
     * @param sourceDocName  来源文档名，空=不限
     * @param limit          返回条数
     */
    @Select("<script>"
            + "SELECT *, MATCH(`content`) AGAINST (#{keyword}) AS keyword_score FROM `bidding_bid_agent_chunk`"
            + " WHERE `deleted` = 0 AND `content` IS NOT NULL"
            + " AND MATCH(`content`) AGAINST (#{keyword})"
            + "<if test='projectId != null'> AND `project_id` = #{projectId}</if>"
            + "<if test='docType != null and docType != \"\"'> AND `doc_type` = #{docType}</if>"
            + "<if test='bidSectionCodes != null and bidSectionCodes.size() > 0'>"
            + " AND (`bid_section_code` IS NOT NULL AND `bid_section_code` IN"
            + "<foreach collection='bidSectionCodes' item='code' open='(' separator=',' close=')'>#{code}</foreach>"
            + "<if test='sectionIncludeUnbound'> OR `doc_type` &lt;&gt; 'TENDER'</if>)"
            + "</if>"
            + "<if test='sourceDocName != null and sourceDocName != \"\"'> AND `source_doc_name` = #{sourceDocName}</if>"
            + " ORDER BY keyword_score DESC, `id` ASC"
            + " LIMIT #{limit}"
            + "</script>")
    List<BidAgentChunkDO> selectByKeyword(@Param("keyword") String keyword,
                                          @Param("projectId") Long projectId,
                                          @Param("docType") String docType,
                                          @Param("bidSectionCodes") List<String> bidSectionCodes,
                                          @Param("sectionIncludeUnbound") boolean sectionIncludeUnbound,
                                          @Param("sourceDocName") String sourceDocName,
                                          @Param("limit") int limit);
}
