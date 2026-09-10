package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "学员端 - 课程学习详情 Response VO")
@Data
public class LearnerCourseDetailRespVO {

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "课程标题", example = "管道防腐保温")
    private String title;

    @Schema(description = "课程封面", example = "https://...")
    private String coverUrl;

    @Schema(description = "课程简介", example = "本课程介绍...")
    private String summary;

    @Schema(description = "课程学分", example = "2.00")
    private BigDecimal credit;

    @Schema(description = "课程总时长（分钟）", example = "45")
    private Integer durationMinutes;

    @Schema(description = "课程内容类型（1 文档 2 视频 3 图文 4 VR）", example = "2")
    private Integer courseMediaType;

    @Schema(description = "讲师", example = "王讲师")
    private String lecturer;

    @Schema(description = "学习任务 ID（未选学为空）", example = "1")
    private Long taskId;

    @Schema(description = "任务状态（0 待学习 1 学习中 2 已完成）", example = "1")
    private Integer taskStatus;

    @Schema(description = "任务来源", example = "assign")
    private String sourceType;

    @Schema(description = "学习进度（%）", example = "40.00")
    private BigDecimal progressPercent;

    @Schema(description = "应获学时", example = "2.00")
    private BigDecimal creditTotal;

    @Schema(description = "已获学时", example = "0.80")
    private BigDecimal creditEarned;

    @Schema(description = "已完成媒资数", example = "2")
    private Integer doneCount;

    @Schema(description = "可学媒资总数", example = "5")
    private Integer totalCount;

    @Schema(description = "首次学习时间", example = "2026-09-04 09:00:00")
    private LocalDateTime startedAt;

    @Schema(description = "完成时间", example = "2026-09-05 18:00:00")
    private LocalDateTime completedAt;

    @Schema(description = "媒资列表（含文档/视频学习状态；图片/VR 仅预览不可学时）")
    private List<LearnerMediaItemVO> medias;

}
