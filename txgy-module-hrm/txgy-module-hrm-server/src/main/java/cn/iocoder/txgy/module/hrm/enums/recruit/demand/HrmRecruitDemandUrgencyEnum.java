package cn.iocoder.txgy.module.hrm.enums.recruit.demand;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * HRM 用工需求单紧急程度枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmRecruitDemandUrgencyEnum {

    NORMAL(1, "普通"),
    URGENT(2, "紧急"),
    CRITICAL(3, "特急");

    private final Integer urgency;
    private final String name;

    public static HrmRecruitDemandUrgencyEnum valueOf(Integer urgency) {
        for (HrmRecruitDemandUrgencyEnum item : values()) {
            if (item.getUrgency().equals(urgency)) {
                return item;
            }
        }
        return null;
    }

}
