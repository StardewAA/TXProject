package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - HRM 员工离职工资结清确认 Request VO")
@Data
public class HrmEmployeeQuitSettleReqVO {

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "员工编号不能为空")
    private Long employeeId;

    @Schema(description = "计薪截止日（为空则保留原值）", example = "2026-10-25")
    private LocalDate salaryCutoffDate;

    @Schema(description = "结算说明", example = "含 10 月工资及借支冲抵，已由财务打款")
    @Size(max = 500, message = "结算说明不能超过 500 个字符")
    private String salarySettleRemark;

}
