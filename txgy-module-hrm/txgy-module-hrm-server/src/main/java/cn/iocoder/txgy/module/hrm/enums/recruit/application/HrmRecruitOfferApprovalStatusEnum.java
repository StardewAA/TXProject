package cn.iocoder.txgy.module.hrm.enums.recruit.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** HRM 招聘 Offer 审批状态。 */
@Getter
@AllArgsConstructor
public enum HrmRecruitOfferApprovalStatusEnum {
    DRAFT("DRAFT", "草稿"), APPROVING("APPROVING", "审批中"), APPROVED("APPROVED", "审批通过"),
    REJECTED("REJECTED", "审批未通过"), WITHDRAWN("WITHDRAWN", "已撤回"), SENT("SENT", "已发出"),
    CONFIRMED("CONFIRMED", "求职者已接受"), EXPIRED("EXPIRED", "已过期"),
    CANDIDATE_REJECTED("CANDIDATE_REJECTED", "求职者已拒绝");

    private final String status;
    private final String name;
}
