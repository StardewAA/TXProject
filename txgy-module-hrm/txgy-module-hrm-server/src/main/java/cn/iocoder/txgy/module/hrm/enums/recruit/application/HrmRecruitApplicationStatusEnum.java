package cn.iocoder.txgy.module.hrm.enums.recruit.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** HRM 招聘应聘状态。 */
@Getter
@AllArgsConstructor
public enum HrmRecruitApplicationStatusEnum {

    SUBMITTED("SUBMITTED", "已投递"),
    SCREENING("SCREENING", "筛选中"),
    RESUME_REJECTED("RESUME_REJECTED", "简历淘汰"),
    SCREENING_PASSED("SCREENING_PASSED", "筛选通过"),
    PENDING_INTERVIEW("PENDING_INTERVIEW", "待约面"),
    INTERVIEW_SCHEDULED("INTERVIEW_SCHEDULED", "已约面"),
    INTERVIEWING("INTERVIEWING", "面试中"),
    INTERVIEW_EVALUATING("INTERVIEW_EVALUATING", "面试评估"),
    NEXT_INTERVIEW("NEXT_INTERVIEW", "下一轮"),
    INTERVIEW_CANCELED("INTERVIEW_CANCELED", "面试取消"),
    INTERVIEW_ABSENT("INTERVIEW_ABSENT", "面试缺席"),
    INTERVIEW_REJECTED("INTERVIEW_REJECTED", "面试淘汰"),
    FINAL_INTERVIEW_PASSED("FINAL_INTERVIEW_PASSED", "终面通过"),
    PENDING_OFFER("PENDING_OFFER", "待发 Offer"),
    PASSED("PASSED", "面试通过"),
    OFFER_APPROVING("OFFER_APPROVING", "Offer审批中"),
    OFFER_SENT("OFFER_SENT", "Offer已发出"),
    OFFER_REJECTED("OFFER_REJECTED", "Offer已拒绝"),
    OFFER_EXPIRED("OFFER_EXPIRED", "Offer已失效"),
    PENDING_ONBOARD("PENDING_ONBOARD", "待入职"),
    ONBOARDED("ONBOARDED", "已入职"),
    ELIMINATED("ELIMINATED", "已淘汰"),
    WITHDRAWN("WITHDRAWN", "已撤回");

    public static final Set<String> PROCESSING_STATUSES = Arrays.stream(values())
            .filter(item -> !item.isTerminal()).map(HrmRecruitApplicationStatusEnum::getStatus)
            .collect(Collectors.toUnmodifiableSet());

    private final String status;
    private final String name;

    public boolean isTerminal() {
        return this == RESUME_REJECTED || this == INTERVIEW_CANCELED
                || this == INTERVIEW_ABSENT || this == INTERVIEW_REJECTED
                || this == OFFER_REJECTED || this == OFFER_EXPIRED
                || this == ONBOARDED || this == ELIMINATED || this == WITHDRAWN;
    }

    public static HrmRecruitApplicationStatusEnum valueOfStatus(String status) {
        return Arrays.stream(values()).filter(item -> item.status.equals(status)).findFirst().orElse(null);
    }
}
