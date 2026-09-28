package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.headcount;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 部门编制 新增/修改 Request VO")
@Data
public class HrmDeptHeadcountSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "部门编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "部门不能为空")
    private Long deptId;

    @Schema(description = "编制人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "30")
    @NotNull(message = "编制人数不能为空")
    @Min(value = 0, message = "编制人数不能小于 0")
    private Integer headcount;

    @Schema(description = "备注", example = "含劳务分包班组 12 人")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
