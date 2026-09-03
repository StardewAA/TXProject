package cn.iocoder.txgy.module.hrm.enums.recruit.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** HRM 招聘 Offer 审批状态。 */
@Getter
@AllArgsConstructor
public enum HrmRecruitOfferApprovalStatusEnum {
    DRAFT("DRAFT", "草稿"), APPROVING("APPROVING", "审批中"), APPROVED("APPROVED", "审批通过"),
    REJECTED("REJECTED", "已驳回"), WITHDRAWN("WITHDRAWN", "已撤回"), SENT("SENT", "已发出"), CONFIRMED("CONFIRMED", "已确认");

    private final String status;
    private final String name;
}
