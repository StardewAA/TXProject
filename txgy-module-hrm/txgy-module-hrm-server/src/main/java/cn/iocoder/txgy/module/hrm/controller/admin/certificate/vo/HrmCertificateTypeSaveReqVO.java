package cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 证照类型创建/修改 Request VO")
@Data
public class HrmCertificateTypeSaveReqVO {

    @Schema(description = "编号（新增留空）", example = "1")
    private Long id;

    @Schema(description = "证照类型名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "高处作业操作证")
    @NotEmpty(message = "证照类型名称不能为空")
    @Size(max = 128, message = "名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "HIGH_WORK")
    @NotEmpty(message = "编码不能为空")
    @Size(max = 64, message = "编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "是否特种作业", example = "true")
    private Boolean specialOperation;

    @Schema(description = "是否必持（缺证不得上岗）", example = "true")
    private Boolean mandatory;

    @Schema(description = "是否需要复审", example = "true")
    private Boolean needReview;

    @Schema(description = "到期预警提前天数", requiredMode = Schema.RequiredMode.REQUIRED, example = "60")
    @NotNull(message = "预警提前天数不能为空")
    private Integer warnDays;

    @Schema(description = "发证机关", example = "应急管理局")
    @Size(max = 128, message = "发证机关长度不能超过 128 个字符")
    private String issuingAuthority;

    @Schema(description = "显示顺序", example = "1")
    private Integer sort;

    @Schema(description = "状态：0 启用，1 停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;

}
