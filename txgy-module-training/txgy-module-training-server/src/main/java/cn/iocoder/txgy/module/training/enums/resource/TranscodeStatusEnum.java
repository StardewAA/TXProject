package cn.iocoder.txgy.module.training.enums.resource;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 视频转码状态枚举（易版：预留状态位，转码动作由人工/后续接入 FFmpeg 流水线）
 */
@Getter
@AllArgsConstructor
public enum TranscodeStatusEnum {

    NONE(0, "未转码"),
    PENDING(1, "排队中"),
    PROCESSING(2, "转码中"),
    SUCCESS(3, "转码成功"),
    FAILED(4, "转码失败");

    private final Integer status;
    private final String name;
}
