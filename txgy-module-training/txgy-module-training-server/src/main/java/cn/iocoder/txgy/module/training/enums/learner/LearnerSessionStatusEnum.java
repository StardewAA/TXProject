package cn.iocoder.txgy.module.training.enums.learner;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 学习会话状态
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum LearnerSessionStatusEnum {

    ACTIVE("active", "进行中"),
    ENDED("ended", "正常结束"),
    INVALID("invalid", "异常无效");

    private final String status;
    private final String name;

    public static LearnerSessionStatusEnum getByStatus(String status) {
        if (status == null) {
            return null;
        }
        return Arrays.stream(values()).filter(item -> item.getStatus().equals(status)).findFirst().orElse(null);
    }

}
