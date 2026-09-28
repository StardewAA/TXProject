package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.changerecord;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 员工异动撤销 Request VO")
@Data
public class HrmEmployeeChangeRecordCancelReqVO {

    @Schema(description = "异动记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "异动记录编号不能为空")
    private Long id;

    @Schema(description = "撤销原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "生效日期填错，重新发起")
    @NotBlank(message = "撤销原因不能为空")
    @Size(max = 500, message = "撤销原因不能超过 500 个字符")
    private String reason;

}
