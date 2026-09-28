package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "学员端 - 个人学习轨迹汇总 Response VO")
@Data
public class LearnerTrackSummaryRespVO {

    @Schema(description = "累计有效学习秒数", example = "7200")
    private Integer totalValidSeconds;

    @Schema(description = "累计已获学时（小时）", example = "2.40")
    private BigDecimal totalCreditEarned;

    @Schema(description = "应获学时合计（小时）", example = "10.00")
    private BigDecimal creditTotal;

    @Schema(description = "课程任务总数", example = "5")
    private Integer taskTotalCount;

    @Schema(description = "已完成课程任务数", example = "2")
    private Integer taskCompletedCount;

    @Schema(description = "学习打卡次数", example = "6")
    private Long checkinCount;

    @Schema(description = "课程维度汇总（已完成的学习课程）")
    private List<CourseRow> courseRows;

    @Schema(description = "媒资类型维度汇总")
    private List<TypeRow> typeRows;

    @Schema(description = "课程维度")
    @Data
    public static class CourseRow {

        @Schema(description = "课程 ID", example = "1")
        private Long courseId;

        @Schema(description = "课程标题", example = "管道防腐保温")
        private String courseTitle;

        @Schema(description = "任务状态：0 待学习 1 学习中 2 已完成", example = "1")
        private Integer taskStatus;

        @Schema(description = "学习进度（%）", example = "40.00")
        private BigDecimal progressPercent;

        @Schema(description = "应获学时", example = "2.00")
        private BigDecimal creditTotal;

        @Schema(description = "已获学时", example = "0.80")
        private BigDecimal creditEarned;

    }

    @Schema(description = "媒资类型维度")
    @Data
    public static class TypeRow {

        @Schema(description = "媒资类型：video/document/live/replay", example = "video")
        private String mediaType;

        @Schema(description = "媒资类型文案", example = "视频")
        private String mediaTypeName;

        @Schema(description = "学习会话数", example = "8")
        private Integer sessionCount;

        @Schema(description = "有效学习秒数", example = "3600")
        private Integer totalValidSeconds;

    }

}
