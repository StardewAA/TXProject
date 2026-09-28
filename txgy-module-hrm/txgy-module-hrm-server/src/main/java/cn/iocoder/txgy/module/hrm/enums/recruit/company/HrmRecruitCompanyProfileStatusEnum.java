package cn.iocoder.txgy.module.hrm.enums.recruit.company;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HrmRecruitCompanyProfileStatusEnum {

    PUBLISHED(0, "已发布"),
    OFFLINE(1, "已下架");

    private final Integer status;
    private final String name;
}
