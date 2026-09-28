package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 所属企业 新增/修改 Request（SaveReqVO）。
 *
 * <p>规范出处：《招投标模块开发规范》§3.1（自建 VO 命名）、§4.5（写接口幂等 + 乐观锁 version）、
 * §五 二（字段长度档位：单位名称 varchar(128) / USCC varchar(32)）。
 */
@Schema(description = "管理后台 - 所属企业 新增/修改 Request")
@Data
public class SubjectCompanySaveReqVO {

    @Schema(description = "所属企业 id；null=新增，非 null=修改")
    private Long id;

    @Schema(description = "乐观锁版本（修改必传）")
    private Integer version;

    @Schema(description = "企业名称（必填；长度档位 128）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "企业名称不能为空")
    @Size(max = 128, message = "企业名称长度不能超过 128 位")
    private String companyName;

    @Schema(description = "统一社会信用代码（GB32100-2015 18 位；L2）")
    @Size(max = 32, message = "统一社会信用代码长度不能超过 32 位")
    private String uscc;

    @Schema(description = "企业类型 code（subject_company_type：SELF 本企业 / SUBSIDIARY 子公司 / BRANCH 分公司）")
    @Size(max = 16, message = "企业类型长度不能超过 16 位")
    private String companyType;

    @Schema(description = "上级企业 id（顶级为 0；用于本企业→子公司从属）")
    private Long parentId;

    @Schema(description = "法定代表人姓名（L3）")
    @Size(max = 64, message = "法定代表人姓名长度不能超过 64 位")
    private String legalPersonName;

    @Schema(description = "联系人（L3）")
    @Size(max = 64, message = "联系人长度不能超过 64 位")
    private String contactName;

    @Schema(description = "联系电话（L3，列表默认掩码）")
    @Size(max = 32, message = "联系电话长度不能超过 32 位")
    private String contactPhone;

    @Schema(description = "行政区划代码")
    @Size(max = 32, message = "行政区划代码长度不能超过 32 位")
    private String regionCode;

    @Schema(description = "所属行业代码")
    @Size(max = 32, message = "所属行业代码长度不能超过 32 位")
    private String industryCode;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 位")
    private String remark;

    @Schema(description = "是否启用（停用只是从选择器隐去，不影响已归属的历史证照/资质）")
    private Boolean enabled;
}
