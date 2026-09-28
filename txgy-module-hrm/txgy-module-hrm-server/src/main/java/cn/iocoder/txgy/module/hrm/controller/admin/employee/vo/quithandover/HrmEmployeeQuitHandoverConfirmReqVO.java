package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 员工离职交接确认 Request VO")
@Data
public class HrmEmployeeQuitHandoverConfirmReqVO {

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "员工编号不能为空")
    private Long employeeId;

    @Schema(description = "交接人（员工编号）", example = "2")
    private Long handoverToEmployeeId;

    @Schema(description = "交接说明", example = "工具已当面点交，工牌已回收")
    @Size(max = 500, message = "交接说明不能超过 500 个字符")
    private String remark;

}
