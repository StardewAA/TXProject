package cn.iocoder.txgy.module.bidding.controller.admin.subject.vo;
// [依据] 《招投标模块开发规范》§3.1（VO 规约）；字段口径见 国家/陕西 V2.0 数据规范 表89 法人基本信息与 docs/法规依据/00-索引与引用说明.md


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 主体信息档案保存 Request（企业租户自维护；subjectType 同租户可各存一条 BIDDER/TENDERER）")
@Data
public class SubjectProfileSaveReqVO {

    @Schema(description = "主体类型：BIDDER/TENDERER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "主体类型不能为空")
    private String subjectType;

    @Schema(description = "单位名称（表89 LEGAL_NAME）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "单位名称不能为空")
    @Size(max = 200, message = "单位名称长度不能超过 200")
    private String subjectName;

    @Schema(description = "统一社会信用代码（GB 32100-2015，18 位含校验码）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "统一社会信用代码不能为空")
    @Pattern(regexp = "^[0-9A-HJ-NP-RTUW-Y]{18}$", message = "统一社会信用代码格式不合法（GB 32100-2015 字符集）")
    private String uscc;

    @Schema(description = "法定代表人姓名（表89 LEGAL_REPRESENT）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "法定代表人姓名不能为空")
    @Size(max = 50)
    private String legalPersonName;

    @Schema(description = "法人机构类别代码（表89 LEGAL_TYPE C1，参考 14.4.2）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "法人机构类别不能为空")
    @Pattern(regexp = "^[0-9A-Z]$", message = "法人机构类别代码须为 1 位字母/数字")
    private String legalPersonTypeCode;

    @Schema(description = "行政区划代码（表89 REGION_CODE C6，GB/T 2260 市级代码）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "行政区划代码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "行政区划代码须为 6 位数字（GB/T 2260）")
    private String regionCode;

    @Schema(description = "联系人（表89 LEGAL_CONTACT）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "联系人不能为空")
    @Size(max = 50)
    private String contactName;

    @Schema(description = "联系人电话（表89 LEGAL_CONTACT_PHONE）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "联系电话不能为空")
    @Size(max = 20)
    private String contactPhone;

    @Schema(description = "行业代码（表89 LEGAL_INDUSTRY C3，GB/T 4754-2011；可选）")
    private String industryCode;

    @Schema(description = "注册资本（可选）")
    private BigDecimal regCapital;

    @Schema(description = "注册资本币种数字码（GB/T 12406-2008；默认 156=人民币）")
    private String regCapCurrency;

    @Schema(description = "营业执照影像 file_id（FileApi 对象主键；可选，实名凭据留档）")
    private Long licenseFileId;

    @Schema(description = "营业执照影像附件名称")
    private String licenseFileName;

    @Schema(description = "权威扩展字段 JSON（key=规范英文名称；可选）")
    private String extJson;

    @Schema(description = "备注")
    private String remark;
}
