package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 工号规则保存 Request VO")
@Data
public class HrmEmployeeNoRuleSaveReqVO {

    @Schema(description = "规则编号（为空为新增）", example = "1")
    private Long id;

    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "秦川默认工号规则")
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 50, message = "规则名称不能超过 50 个字符")
    private String name;

    @Schema(description = "租户缩写（工号前缀）", requiredMode = Schema.RequiredMode.REQUIRED, example = "QC")
    @NotBlank(message = "租户缩写不能为空")
    @Size(max = 10, message = "租户缩写不能超过 10 个字符")
    private String tenantAbbr;

    @Schema(description = "顺序号位数（左补零）", requiredMode = Schema.RequiredMode.REQUIRED, example = "6")
    @NotNull(message = "顺序号位数不能为空")
    @Min(value = 1, message = "顺序号位数不能小于 1")
    @Max(value = 12, message = "顺序号位数不能超过 12")
    private Integer serialLength;

    @Schema(description = "前缀与序号之间的分隔符，默认空", example = "-")
    @Size(max = 3, message = "分隔符不能超过 3 个字符")
    private String prefixSeparator;

    @Schema(description = "重置周期：0 不重置，1 按年重置", example = "0")
    private Integer resetPeriod;

    @Schema(description = "是否允许人工指定工号", example = "false")
    private Boolean allowManual;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "工号示例：QC000001")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
