package cn.iocoder.txgy.module.hrm.dal.dataobject.agent;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * HRM 招聘 Agent 简历优化项 DO（A03）。
 *
 * <p>以「建议项」为最小确认单位记录一次确认的结果：
 * 模块、字段路径、原值、模型建议值、用户最终值与采纳状态。
 * 有了这张表，「用户采纳情况」才能被审计，而不只是体现在新版本内容里。</p>
 */
@TableName("hrm_recruit_agent_optimize_item")
@KeySequence("hrm_recruit_agent_optimize_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitAgentOptimizeItemDO extends TenantBaseDO {

    /** 采纳状态：接受建议值 */
    public static final String DECISION_ACCEPT = "ACCEPT";
    /** 采纳状态：拒绝建议，保持原值 */
    public static final String DECISION_REJECT = "REJECT";
    /** 采纳状态：在建议基础上手工调整 */
    public static final String DECISION_ADJUST = "ADJUST";

    @TableId
    private Long id;
    /** 所属简历优化任务编号 */
    private Long taskId;
    /** 归属用户编号 */
    private Long userId;
    /** 排序，与建议项顺序一致 */
    private Integer seq;
    /** 模块，见简历模块枚举 */
    private String module;
    /** 字段路径 */
    private String fieldPath;
    /** 原值（源版本中逐字出现的原文片段） */
    private String originalValue;
    /** 模型建议值 */
    private String suggestedValue;
    /** 用户最终确认值 */
    private String finalValue;
    /** 采纳状态：ACCEPT、REJECT、ADJUST */
    private String decision;
    /** 确认后生成的个人简历版本编号 */
    private Long resultVersionId;
}
