package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理后台 - 学员整体学习进度（学习中心「学员学习进度」视图）
 *
 * <p>统计口径：学员在「培训班学习任务（training_student_task）」与
 * 「学习任务（training_learner_task）」中的全部课程任务按 学员+课程 去重后聚合。</p>
 *
 * @author 云培训学院课题小组
 */
@Schema(description = "管理后台 - 学员整体学习进度 Response VO")
@Data
public class LearnerProgressRespVO {

    @Schema(description = "学员用户 ID", example = "3001")
    private Long userId;

    @Schema(description = "学员姓名", example = "张三")
    private String userName;

    @Schema(description = "所属部门", example = "防腐涂装车间")
    private String deptName;

    @Schema(description = "所在培训班名称（多个以「、」拼接）", example = "2026 年管道工岗前培训（一期）")
    private String className;

    @Schema(description = "任务总数（课程数）", example = "8")
    private Integer totalTasks;

    @Schema(description = "待学习数", example = "3")
    private Integer todoCount;

    @Schema(description = "学习中数", example = "2")
    private Integer learningCount;

    @Schema(description = "已完成数", example = "3")
    private Integer completedCount;

    @Schema(description = "整体学习进度（%）", example = "46.50")
    private BigDecimal progressPercent;

    @Schema(description = "应获学时合计（小时）", example = "20.00")
    private BigDecimal creditTotal;

    @Schema(description = "已获学时合计（小时）", example = "9.30")
    private BigDecimal creditEarned;

}
