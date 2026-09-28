package cn.iocoder.txgy.module.hrm.enums.recruit.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 招聘沟通会话状态。 */
@Getter
@AllArgsConstructor
public enum HrmRecruitChatStatusEnum {

    NOT_STARTED(0, "待发起"),
    WAITING(1, "等待回复"),
    QUALIFIED(2, "招聘方已回复"),
    FAILED(3, "沟通失败");

    private final Integer status;
    private final String name;

    public static HrmRecruitChatStatusEnum valueOfStatus(Integer status) {
        if (status == null) {
            return WAITING;
        }
        for (HrmRecruitChatStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return WAITING;
    }
}
