package cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent;

// [规范依据]《招投标模块开发规范》§4.2
// [业务目标] 精准度测试用例（golden set）：专员在后台维护"标准问题 → 期望命中"，一键跑批得到 Recall@K
// [为什么] 文档要求"知识库精准度测试"，而评测必须可重复、可追溯；用例落库后指标才有基线可对比

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("bidding_bid_eval_case")
@Data
@EqualsAndHashCode(callSuper = true)
public class BidAgentEvalCaseDO extends TenantBaseDO {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 用例名称 */
    private String name;
    /** 标准问题（检索词） */
    private String query;
    /** 限定材料角色，空=不限 */
    private String docType;
    /** 限定标段，空=不限 */
    private String bidSectionCode;
    /** 评测取前 K 条 */
    private Integer topK;
    /** 期望命中的关键词（半角逗号分隔，命中任一即算命中） */
    private String expectKeywords;
    /** 期望命中的溯源块编号（半角逗号分隔，可选，优先级高于关键词） */
    private String expectBlockIds;
    /** 是否启用：1 是 0 否 */
    private Integer enabled;
    /** 备注 */
    private String remark;
}
