package cn.iocoder.txgy.module.bidding.dal.dataobject.review;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 平台审查任务 DO。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§2 数据模型（字段逐字对齐）。
 *
 * <p>为什么这样写（设计遵循）：
 * <ul>
 *   <li>企业名称/统一社会信用代码/证书编号都是**快照**：审查者要"一眼区分来源企业"，
 *       而企业档案后续可能被改名 —— 若实时联表，历史审查记录会随主数据漂移，追溯就失去意义。</li>
 *   <li>驳回原因 `reason` 是**租户可见**字段（结果回传），与内部备注区分开。</li>
 *   <li>`version` 乐观锁：通过/驳回必须回传（契约 §4 与 §0.7），并发审查同一任务时后提交者收到冲突而非静默覆盖。</li>
 * </ul>
 *
 * <p>业务目标：把"企业录入"与"平台审查"解耦为可筛选、可追溯的任务流；通过后证照/资质方可被配证、申报类接口引用。
 *
 * @author txgy
 */
@TableName("bidding_review_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewTaskDO extends TenantBaseDO {

    /** 主键 */
    private Long id;
    /** 业务类型：QUALIFICATION/CERTIFICATE/PERSON_CERT（字典 review_biz_type） */
    private String bizType;
    /** 关联业务主键（资质 id / 证照 id） */
    private Long bizId;
    /** 提交企业 id（租户隔离维度） */
    private Long entId;
    /** 提交企业名称（快照） */
    private String entName;
    /** 提交企业统一社会信用代码（快照） */
    private String entUscc;
    /** 关联证书编号/资质证书编号（快照） */
    private String refNo;
    /** 任务产生（企业录入）时间 */
    private LocalDateTime submitTime;
    /** PENDING 待审查 / APPROVED 已通过 / REJECTED 已驳回 */
    private String status;
    /** 审查人 */
    private String reviewer;
    /** 审查时间 */
    private LocalDateTime reviewTime;
    /** 驳回原因（REJECTED 必填，租户可见） */
    private String reason;
    /** 提交时的备注 */
    private String remark;
    /** 乐观锁版本 */
    private Integer version;

}
