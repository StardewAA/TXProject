package cn.iocoder.txgy.module.training.enums.learner;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 学员学习任务状态
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum LearnerTaskStatusEnum {

    TODO(0, "待学习"),
    LEARNING(1, "学习中"),
    COMPLETED(2, "已完成");

    private final Integer status;
    private final String name;

    public static LearnerTaskStatusEnum getByStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return Arrays.stream(values()).filter(item -> item.getStatus().equals(status)).findFirst().orElse(null);
    }

}
