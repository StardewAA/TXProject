package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "学员端 - 开启学习会话 Response VO")
@Data
public class LearnerSessionStartRespVO {

    @Schema(description = "学习会话 ID", example = "1")
    private Long sessionId;

    @Schema(description = "状态：active", example = "active")
    private String status;

    @Schema(description = "媒资类型", example = "video")
    private String mediaType;

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "媒资 ID", example = "1")
    private Long mediaId;

    @Schema(description = "任务 ID", example = "1")
    private Long taskId;

    @Schema(description = "心跳间隔（秒），前端按此定时上报", example = "5")
    private Integer heartbeatIntervalSeconds;

    @Schema(description = "会话当前有效秒数", example = "0")
    private Integer validSeconds;

}
