package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 学习数据统计 Response VO（按课程汇总）")
@Data
public class LearningStatRespVO {

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "课程标题", example = "管道防腐保温")
    private String courseTitle;

    @Schema(description = "课程封面", example = "https://...")
    private String coverUrl;

    @Schema(description = "任务总数", example = "10")
    private Integer taskTotal;

    @Schema(description = "待学习数", example = "3")
    private Integer todoCount;

    @Schema(description = "学习中数", example = "4")
    private Integer learningCount;

    @Schema(description = "已完成数", example = "3")
    private Integer completedCount;

    @Schema(description = "平均学习进度（%）", example = "46.50")
    private BigDecimal avgProgressPercent;

    @Schema(description = "应获学时合计（小时）", example = "20.00")
    private BigDecimal creditTotal;

    @Schema(description = "已获学时合计（小时）", example = "9.30")
    private BigDecimal creditEarned;

    @Schema(description = "完成率（%）", example = "30.00")
    private BigDecimal completedRate;

}
