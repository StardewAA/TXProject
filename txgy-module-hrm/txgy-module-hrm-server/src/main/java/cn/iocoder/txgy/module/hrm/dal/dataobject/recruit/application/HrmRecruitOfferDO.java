package cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("hrm_recruit_offer")
@KeySequence("hrm_recruit_offer_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitOfferDO extends TenantBaseDO {
    @TableId private Long id;
    private Long applicationId;
    private String offerNo;
    private String approvalStatus;
    /**
     * 录用通知发送状态：0 未发送 / 1 已发送 / 2 发送失败
     *
     * <p>HR 在 Offer 上直接可见，失败时配合 {@link #notifyFailReason} 支持一键重发（交接文档 ①）。</p>
     */
    private Integer notifyStatus;
    /**
     * 录用通知最近发送时间
     */
    private LocalDateTime notifyTime;
    /**
     * 录用通知发送失败原因
     */
    private String notifyFailReason;
    private BigDecimal salaryAmount;
    private Integer salaryUnit;
    private Long postId;
    private Long entryPostId;
    private LocalDate plannedOnboardDate;
    private LocalDate validUntil;
    private String attachmentIds;
    private String processInstanceId;
    private Integer approvalVersion;
    /** 审批人用户编号（人工审批或工作流审批的最终审批人） */
    private Long approverUserId;
    /** 审批时间 */
    private LocalDateTime approveTime;
    /** 审批意见（通过与驳回都记录） */
    private String approveRemark;
    private Long employeeId;
    private LocalDateTime sentTime;
    private LocalDateTime confirmTime;
    private String remark;
}
