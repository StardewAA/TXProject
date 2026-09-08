package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "学员端 - 学习会话结束/打卡 Response VO")
@Data
public class LearnerSessionEndRespVO {

    @Schema(description = "学习会话 ID", example = "1")
    private Long sessionId;

    @Schema(description = "会话状态：ended/invalid", example = "ended")
    private String status;

    @Schema(description = "结束原因", example = "normal")
    private String reason;

    @Schema(description = "本会话累计有效秒数", example = "300")
    private Integer validSeconds;

    @Schema(description = "任务 ID（课程学习会话有值）", example = "1")
    private Long taskId;

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "是否已完成本媒资", example = "false")
    private Boolean mediaCompleted;

    @Schema(description = "任务是否已全部完成", example = "false")
    private Boolean taskCompleted;

    @Schema(description = "任务学习进度（%）", example = "40.00")
    private BigDecimal progressPercent;

    @Schema(description = "应获学时", example = "2.00")
    private BigDecimal creditTotal;

    @Schema(description = "已获学时", example = "0.80")
    private BigDecimal creditEarned;

    @Schema(description = "已完成媒资数", example = "2")
    private Integer doneCount;

    @Schema(description = "可学媒资总数", example = "5")
    private Integer totalCount;

    @Schema(description = "是否已打卡（仅打卡接口返回）", example = "true")
    private Boolean checkedIn;

}
