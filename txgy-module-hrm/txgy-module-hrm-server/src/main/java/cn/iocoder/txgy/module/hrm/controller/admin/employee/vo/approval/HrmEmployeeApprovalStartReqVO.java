package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 员工异动审批发起 Request VO")
@Data
public class HrmEmployeeApprovalStartReqVO {

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "131")
    @NotNull(message = "员工编号不能为空")
    private Long employeeId;

    @Schema(description = "审批类型：1 转正，2 调岗/晋升，3 离职", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "审批类型不能为空")
    private Integer type;

    @Schema(description = "异动原因/申请说明", example = "试用期考核合格，申请转正")
    private String reason;

    @Schema(description = "期望生效时间", example = "2026-10-01 00:00:00")
    private LocalDateTime effectTime;

    @Schema(description = "备注")
    private String remark;

}
