package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.quithandover;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 员工离职「无需交接」Request VO")
@Data
public class HrmEmployeeQuitHandoverWaiveReqVO {

    @Schema(description = "员工编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "员工编号不能为空")
    private Long employeeId;

    @Schema(description = "无需交接原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "非现场岗位，无公司物品")
    @NotBlank(message = "无需交接原因不能为空")
    @Size(max = 500, message = "无需交接原因不能超过 500 个字符")
    private String remark;

}
