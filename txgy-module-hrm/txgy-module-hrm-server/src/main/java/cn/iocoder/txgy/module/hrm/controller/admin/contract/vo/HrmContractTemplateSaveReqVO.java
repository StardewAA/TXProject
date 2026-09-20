package cn.iocoder.txgy.module.hrm.controller.admin.contract.vo;

import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmTemplateCategoryEnum;
import cn.iocoder.txgy.module.hrm.enums.employee.employment.HrmEmployeeContractTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 合同模板保存 Request VO")
@Data
public class HrmContractTemplateSaveReqVO {

    @Schema(description = "编号；为空表示新增", example = "1")
    private Long id;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "劳务合同（劳务关系）模板")
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称不能超过 128 个字符")
    private String name;

    @Schema(description = "模板分类：1 劳动合同，2 个人电子档案，3 培训协议",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "模板分类不能为空")
    @InEnum(value = HrmTemplateCategoryEnum.class, message = "模板分类必须是 {value}")
    private Integer category;

    @Schema(description = "合同类型（仅「劳动合同」分类需要填写）", example = "5")
    @InEnum(value = HrmEmployeeContractTypeEnum.class, message = "合同类型必须是 {value}")
    private Integer contractType;

    @Schema(description = "适用用工形式", example = "劳务")
    @Size(max = 32, message = "适用用工形式不能超过 32 个字符")
    private String employType;

    @Schema(description = "适用用工主体编号（hrm_company_profile.id）；为空表示不限", example = "1")
    private Long employerId;

    @Schema(description = "版本号", example = "1")
    private Integer version;

    @Schema(description = "状态：0 启用，1 停用", example = "0")
    private Integer status;

    @Schema(description = "是否该类型默认模板", example = "true")
    private Boolean defaultStatus;

    @Schema(description = "模板文件地址（docx）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "http://localhost:48080/admin-api/infra/file/4/get/xxx.docx")
    @NotBlank(message = "模板文件不能为空")
    @Size(max = 512, message = "模板文件地址不能超过 512 个字符")
    private String fileUrl;

    @Schema(description = "模板文件 SHA-256")
    @Size(max = 64, message = "文件哈希不能超过 64 个字符")
    private String fileHash;

    @Schema(description = "缩略图地址（PNG，列表卡片展示）")
    @Size(max = 512, message = "缩略图地址不能超过 512 个字符")
    private String thumbnailUrl;

    @Schema(description = "预览文件地址（PDF，浏览器内嵌预览）")
    @Size(max = 512, message = "预览文件地址不能超过 512 个字符")
    private String previewUrl;

    @Schema(description = "显示顺序（同分类内排序）", example = "1")
    private Integer sort;

    @Schema(description = "来源目录（批量导入时保留）", example = "劳动合同模板")
    @Size(max = 128, message = "来源目录不能超过 128 个字符")
    private String sourceDir;

    @Schema(description = "模板变量清单（逗号分隔）",
            example = "${companyName},${companyCreditCode},${employeeName},${jobNumber}")
    @Size(max = 2000, message = "模板变量清单不能超过 2000 个字符")
    private String variables;

    @Schema(description = "本次变更说明", example = "根据 2026 版劳务合同示范文本修订第三条")
    @Size(max = 500, message = "变更说明不能超过 500 个字符")
    private String changeNote;

    @Schema(description = "备注")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

}
