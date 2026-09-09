package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "学员端 - 个人学习轨迹项 Response VO")
@Data
public class LearnerTrackRespVO {

    @Schema(description = "学习会话 ID", example = "1001")
    private Long sessionId;

    @Schema(description = "课程 ID（直播/回放为 null）", example = "1")
    private Long courseId;

    @Schema(description = "课程标题", example = "管道防腐保温")
    private String courseTitle;

    @Schema(description = "媒资 ID（课程媒资或直播房间 ID）", example = "11")
    private Long mediaId;

    @Schema(description = "学习媒资类型：video/document/live/replay", example = "video")
    private String mediaType;

    @Schema(description = "媒资类型文案", example = "视频")
    private String mediaTypeName;

    @Schema(description = "资源名称（媒资标题或直播标题）", example = "第一章 课程介绍")
    private String mediaName;

    @Schema(description = "开始时间")
    private LocalDateTime startAt;

    @Schema(description = "结束时间")
    private LocalDateTime endAt;

    @Schema(description = "有效学习秒数", example = "300")
    private Integer validSeconds;

    @Schema(description = "结束原因", example = "normal")
    private String endReason;

    @Schema(description = "折算学时（小时）", example = "0.10")
    private BigDecimal creditEarned;

    @Schema(description = "该媒资是否已完成", example = "false")
    private Boolean completed;

}
