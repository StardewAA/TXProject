package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 证件类型新增/修改 Request VO")
@Data
public class HrmCertificateTypeSaveReqVO {

    @Schema(description = "证件类型编号", example = "1024")
    private Long id;

    @Schema(description = "证件类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "特种作业操作证")
    @NotBlank(message = "证件类型名称不能为空")
    @Size(max = 128, message = "证件类型名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "证件类型编码", example = "SPECIAL_OPERATION")
    @Size(max = 64, message = "证件类型编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "是否特种作业", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "是否特种作业不能为空")
    private Boolean specialOperation;

    @Schema(description = "是否需要复审", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "是否需要复审不能为空")
    private Boolean needReview;

    @Schema(description = "复审周期（月）", example = "36")
    @Min(value = 1, message = "复审周期不能小于 1 个月")
    @Max(value = 600, message = "复审周期不能大于 600 个月")
    private Integer reviewCycleMonths;

    @Schema(description = "默认提前预警天数", requiredMode = Schema.RequiredMode.REQUIRED, example = "30")
    @NotNull(message = "默认提前预警天数不能为空")
    @Min(value = 0, message = "默认提前预警天数不能小于 0")
    @Max(value = 365, message = "默认提前预警天数不能大于 365")
    private Integer advanceDays;

    @Schema(description = "是否必须上传证件附件", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    @NotNull(message = "是否必须上传证件附件不能为空")
    private Boolean requireAttachment;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "显示顺序", example = "10")
    @Min(value = 0, message = "显示顺序不能小于 0")
    private Integer sort;

    @Schema(description = "备注", example = "应急管理部门颁发，需按期复审")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
