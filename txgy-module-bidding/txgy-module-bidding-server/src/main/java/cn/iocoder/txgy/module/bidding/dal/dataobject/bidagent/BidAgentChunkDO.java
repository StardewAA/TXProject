package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 投标检索片段 DO：招标文件/企业资料切分后的可检索片段，用于溯源回表。
 */
@TableName("bidding_bid_agent_chunk")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class BidAgentChunkDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 向量库中的向量 ID（与 Document.id 一致）
     */
    private String vectorId;

    /**
     * 项目编号（企业资料可为空）
     */
    private Long projectId;

    /**
     * 材料编号（bidding_bid_material.id）。材料侧入库/清理/分页均按此精确级联，
     * 避免同名文件互相误删；契约端按文档名入库的切片此列为空。
     */
    private Long materialId;

    /**
     * 文档类型：TENDER（招标文件）/ FACT（企业资料）/ TEMPLATE（模板）
     */
    private String docType;

    /**
     * 标段编号：TENDER 类材料必填，检索按标段隔离（2026-09-16 起随检索链上云一并生效）
     */
    private String bidSectionCode;

    /**
     * 来源文档名
     */
    private String sourceDocName;

    /**
     * 章节路径
     */
    private String chapterPath;

    /**
     * 页码
     */
    private Integer pageNo;

    /**
     * 溯源块编号，逗号分隔
     */
    private String sourceBlockIds;

    /**
     * 片段内容
     */
    private String content;

    /**
     * 是否被人工编辑过：0 否 1 是（切片管理「编辑/拆分/合并」后置 1，供质量巡检高亮）
     */
    private Integer edited;

    /** MySQL FULLTEXT/BM25 命中的原始相关度分，仅查询时使用，不落库。 */
    @TableField(exist = false)
    private Double keywordScore;
}
