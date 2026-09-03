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
    INTERVIEWING("INTERVIEWING", "面试中"),
    PASSED("PASSED", "面试通过"),
    OFFER_APPROVING("OFFER_APPROVING", "Offer审批中"),
    OFFER_SENT("OFFER_SENT", "已发Offer"),
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
        return this == ONBOARDED || this == ELIMINATED || this == WITHDRAWN;
    }

    public static HrmRecruitApplicationStatusEnum valueOfStatus(String status) {
        return Arrays.stream(values()).filter(item -> item.status.equals(status)).findFirst().orElse(null);
    }
}
