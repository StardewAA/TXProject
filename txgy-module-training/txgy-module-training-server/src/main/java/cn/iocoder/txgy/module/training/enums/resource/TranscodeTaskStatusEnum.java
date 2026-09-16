package cn.iocoder.txgy.module.training.enums.resource;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 视频转码任务状态枚举
 *
 * @author 云培训学院课题小组
 */
@Getter
@AllArgsConstructor
public enum TranscodeTaskStatusEnum {

    PENDING(0, "待转码"),
    PROCESSING(1, "转码中"),
    SUCCESS(2, "转码成功"),
    FAILED(3, "转码失败"),
    SKIPPED(4, "已跳过");

    private final Integer status;
    private final String name;

    public static TranscodeTaskStatusEnum fromStatus(Integer status) {
        return Arrays.stream(values())
                .filter(item -> item.status.equals(status))
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否为终态（不会再变化）
     */
    public static boolean isFinished(Integer status) {
        TranscodeTaskStatusEnum e = fromStatus(status);
        return e == SUCCESS || e == FAILED || e == SKIPPED;
    }
}
