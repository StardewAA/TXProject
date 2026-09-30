package cn.iocoder.txgy.module.hrm.controller.admin.attendance.vo.leave;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 请假申请人工审批 Request VO")
@Data
public class HrmAttendanceLeaveApproveReqVO {

    @Schema(description = "请假申请编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "请假申请编号不能为空")
    private Long id;

    @Schema(description = "是否通过：true 通过，false 驳回", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "true")
    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    @Schema(description = "审批意见；驳回时必填", example = "现场赶工期，暂不批假")
    @Size(max = 500, message = "审批意见不能超过 500 个字符")
    private String reason;

}
