package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "学员端 - 我的课程卡片 Response VO")
@Data
public class LearnerCourseCardRespVO {

    @Schema(description = "学习任务 ID", example = "1")
    private Long taskId;

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "任务来源：assign 指派 / self 自主选学", example = "assign")
    private String sourceType;

    @Schema(description = "任务状态：0 待学习 1 学习中 2 已完成", example = "1")
    private Integer status;

    @Schema(description = "课程标题", example = "管道防腐保温")
    private String courseTitle;

    @Schema(description = "课程封面", example = "https://...")
    private String coverUrl;

    @Schema(description = "课程讲师", example = "王讲师")
    private String lecturer;

    @Schema(description = "课程学分（小时）", example = "2.00")
    private BigDecimal courseCredit;

    @Schema(description = "课程内容类型（1 文档 2 视频 3 图文 4 VR）", example = "2")
    private Integer courseMediaType;

    @Schema(description = "课程总时长（分钟）", example = "45")
    private Integer courseDurationMinutes;

    @Schema(description = "可学媒资总数", example = "5")
    private Integer totalCount;

    @Schema(description = "已完成媒资数", example = "2")
    private Integer doneCount;

    @Schema(description = "学习进度（%），0-100", example = "40.00")
    private BigDecimal progressPercent;

    @Schema(description = "应获学时（小时）", example = "2.00")
    private BigDecimal creditTotal;

    @Schema(description = "已获学时（小时）", example = "0.80")
    private BigDecimal creditEarned;

    @Schema(description = "首次学习时间", example = "2026-09-04 09:00:00")
    private LocalDateTime startedAt;

    @Schema(description = "完成时间", example = "2026-09-05 18:00:00")
    private LocalDateTime completedAt;

    @Schema(description = "任务创建时间", example = "2026-09-04 08:00:00")
    private LocalDateTime createTime;

}
