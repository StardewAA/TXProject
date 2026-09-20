package cn.iocoder.txgy.module.hrm.controller.admin.policy.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - HRM 政策新增/修改 Request VO")
@Data
public class HrmPolicySaveReqVO {

    @Schema(description = "政策编号")
    private Long id;

    @Schema(description = "政策名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "政策名称不能为空")
    @Size(max = 100, message = "政策名称长度不能超过 100 个字符")
    private String name;

    @Schema(description = "类别：social_insurance / provident_fund / labor_contract / training / other")
    @Size(max = 32, message = "类别长度不能超过 32 个字符")
    private String category;

    @Schema(description = "文号")
    @Size(max = 64, message = "文号长度不能超过 64 个字符")
    private String docNo;

    @Schema(description = "发布机关")
    @Size(max = 100, message = "发布机关长度不能超过 100 个字符")
    private String publishOrg;

    @Schema(description = "发布日期")
    private LocalDate publishDate;

    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    @Schema(description = "失效日期")
    private LocalDate expireDate;

    @Schema(description = "适用范围：group / tenant / region")
    @Size(max = 16, message = "适用范围长度不能超过 16 个字符")
    private String scope;

    @Schema(description = "范围明细（地区名称等）")
    @Size(max = 64, message = "范围明细长度不能超过 64 个字符")
    private String scopeValue;

    @Schema(description = "政策正文")
    private String content;

    @Schema(description = "附件地址")
    @Size(max = 10, message = "附件最多 10 个")
    private List<String> fileUrls;

    @Schema(description = "标签")
    @Size(max = 10, message = "标签最多 10 个")
    private List<String> tags;

    @Schema(description = "状态：0 启用 1 停用")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过 500 个字符")
    private String remark;
}
