package cn.iocoder.txgy.module.training.enums.plan;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 学员学习任务状态枚举
 *
 * 用于培训任务总状态 status 与课程完成状态 course_status：
 * 0 未开始 / 1 进行中 / 2 已完成
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum TaskStatusEnum {

    NOT_STARTED(0, "未开始"),
    IN_PROGRESS(1, "进行中"),
    FINISHED(2, "已完成");

    private final Integer status;
    private final String name;

    public static TaskStatusEnum getByStatus(Integer status) {
        return Arrays.stream(values()).filter(item -> item.getStatus().equals(status)).findFirst().orElse(null);
    }

    public static boolean isValid(Integer status) {
        return getByStatus(status) != null;
    }

}
