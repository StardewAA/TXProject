package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 员工异动审批回调 Request VO")
@Data
public class HrmEmployeeApprovalCallbackReqVO {

    @Schema(description = "申请单编号（传了就按编号审批；不传则按员工+类型取最新待审批）", example = "1")
    private Long id;

    @Schema(description = "员工编号（不传 id 时必填，用于按员工+类型定位待审批单）", example = "131")
    private Long employeeId;

    @Schema(description = "审批类型（不传 id 时必填）：1 转正，2 调岗/晋升，3 离职", example = "1")
    private Integer type;

    @Schema(description = "是否审批通过", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    @Schema(description = "审批意见", example = "同意")
    private String comment;

}
