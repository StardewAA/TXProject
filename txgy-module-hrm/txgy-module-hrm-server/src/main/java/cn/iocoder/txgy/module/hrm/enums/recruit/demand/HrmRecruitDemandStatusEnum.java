package cn.iocoder.txgy.module.hrm.enums.recruit.demand;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * HRM 用工需求单状态枚举
 *
 * @author OPENLAB BS
 */
@Getter
@AllArgsConstructor
public enum HrmRecruitDemandStatusEnum {

    PENDING(1, "待招聘"),
    RECRUITING(2, "招聘中"),
    FINISHED(3, "已完成"),
    CANCELED(4, "已取消");

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 名称
     */
    private final String name;

    public static HrmRecruitDemandStatusEnum valueOf(Integer status) {
        for (HrmRecruitDemandStatusEnum item : values()) {
            if (item.getStatus().equals(status)) {
                return item;
            }
        }
        return null;
    }

}
