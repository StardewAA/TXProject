package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.option;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 工资表薪资项 修改 Request VO")
@Data
public class HrmSalaryOptionUpdateReqVO {

    @Schema(description = "薪资项编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "薪资项编号不能为空")
    private Long id;

    @Schema(description = "薪资项名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "高温补贴")
    @NotBlank(message = "薪资项名称不能为空")
    @Size(max = 64, message = "薪资项名称长度不能超过 64 个字符")
    private String name;

    @Schema(description = "单位（如 元/月、元/天、次、%）", example = "元/月")
    @Size(max = 16, message = "单位长度不能超过 16 个字符")
    private String unit;

    @Schema(description = "备注", example = "按出勤月发放")
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;

}
