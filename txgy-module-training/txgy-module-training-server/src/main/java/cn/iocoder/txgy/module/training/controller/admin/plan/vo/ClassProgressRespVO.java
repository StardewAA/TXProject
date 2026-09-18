package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 班级学习进度 Response VO（按学员聚合）")
@Data
public class ClassProgressRespVO {

    @Schema(description = "学员用户 ID", example = "3001")
    private Long userId;

    @Schema(description = "学员姓名", example = "张三")
    private String userName;

    @Schema(description = "部门/班组", example = "管道一班")
    private String deptName;

    @Schema(description = "任务总数（绑定课程数）", example = "3")
    private Integer totalTasks;

    @Schema(description = "已完成任务数", example = "1")
    private Integer finishedTasks;

    @Schema(description = "学时是否全部完成", example = "false")
    private Boolean studyFinished;

    @Schema(description = "课程是否全部完成", example = "false")
    private Boolean courseFinished;

    /**
     * 考试聚合状态：0 未参加/待考试 1 全部通过 2 存在未通过
     */
    @Schema(description = "考试聚合状态：0未参加 1全部通过 2存在未通过", example = "0")
    private Integer examStatus;

    @Schema(description = "证书是否全部获取", example = "false")
    private Boolean certFinished;

    /**
     * 学员进度维度（用于筛选展示）：0 未开始 1 进行中 2 已完成 3 考试未通过
     */
    @Schema(description = "进度维度：0未开始 1进行中 2已完成 3考试未通过", example = "0")
    private Integer overallStatus;

    @Schema(description = "完成百分比", example = "33")
    private Integer progressPercent;

}
