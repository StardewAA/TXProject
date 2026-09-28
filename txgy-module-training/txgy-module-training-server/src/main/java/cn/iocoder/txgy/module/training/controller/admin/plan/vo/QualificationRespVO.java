package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 上岗资格核查 Response VO")
@Data
public class QualificationRespVO {

    @Schema(description = "人员 ID（平台账号）", example = "3001")
    private Long userId;

    @Schema(description = "人员姓名", example = "张三")
    private String userName;

    @Schema(description = "核查输入的手机号", example = "13800138000")
    private String mobile;

    /**
     * true=可上岗 false=不可上岗
     */
    @Schema(description = "是否具备上岗资格", example = "true")
    private Boolean qualified;

    @Schema(description = "通过岗位（合格培训班配置的目标岗位，去重）", example = "[\"管道工\",\"防腐工\"]")
    private List<String> passedPosts;

    @Schema(description = "核查明细（岗前/复工等各类培训完成情况）")
    private List<QualificationItemVO> items;

    @Schema(description = "原因说明", example = "岗前培训合格，复工培训合格")
    private String reason;

    @Schema(description = "各培训完成情况 Item VO")
    @Data
    public static class QualificationItemVO {

        @Schema(description = "培训班 ID", example = "1")
        private Long classId;

        @Schema(description = "培训班名称", example = "2026 年管道工岗前培训（一期）")
        private String className;

        @Schema(description = "培训类型：1岗前 2复工", example = "1")
        private Integer type;

        @Schema(description = "培训类型名称", example = "岗前培训")
        private String typeName;

        @Schema(description = "培训班状态", example = "1")
        private Integer classStatus;

        @Schema(description = "培训截止时间", example = "2026-09-30 23:59:59")
        private LocalDateTime deadline;

        @Schema(description = "该人员在此培训班任务数", example = "3")
        private Integer totalTasks;

        @Schema(description = "已完成任务数", example = "3")
        private Integer finishedTasks;

        @Schema(description = "是否合格（任务全部完成）", example = "true")
        private Boolean qualified;

    }

}
