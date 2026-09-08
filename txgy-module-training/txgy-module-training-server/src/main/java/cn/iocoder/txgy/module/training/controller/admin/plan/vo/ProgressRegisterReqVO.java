package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 学员学习进度登记 Request VO（线下/教务代录入口；正式由学员端学习记录与考试模块推进）")
@Data
public class ProgressRegisterReqVO {

    @Schema(description = "学习任务 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "学习任务 ID 不能为空")
    private Long taskId;

    @Schema(description = "学时是否完成（true 标为已完成）", example = "true")
    private Boolean studyFinished;

    @Schema(description = "课程完成状态：0未开始 1进行中 2已完成", example = "2")
    private Integer courseStatus;

    @Schema(description = "考试结果：0未参加 1已通过 2未通过", example = "1")
    private Integer examStatus;

    @Schema(description = "证书是否获取（true 标为已获取）", example = "true")
    private Boolean certStatus;

}
