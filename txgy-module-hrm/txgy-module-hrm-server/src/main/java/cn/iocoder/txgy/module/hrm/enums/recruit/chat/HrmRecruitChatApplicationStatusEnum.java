package cn.iocoder.txgy.module.hrm.enums.recruit.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 招聘沟通对应的简历投递状态。 */
@Getter
@AllArgsConstructor
public enum HrmRecruitChatApplicationStatusEnum {

    READY_TO_SUBMIT("READY_TO_SUBMIT", "待投递"),
    SUBMITTED("SUBMITTED", "已投递");

    private final String status;
    private final String name;
}
