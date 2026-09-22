package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "HRM 招聘企业档案")
@Data
public class HrmRecruitCompanyProfileVO {
    @Schema(description = "企业名称")
    @Size(max = 255)
    private String companyName;
    @Schema(description = "是否由 HR 手动确认过企业名称")
    private Boolean companyNameCustomized;
    @Schema(description = "企业类型")
    @Size(max = 255)
    private String companyType;
    @Schema(description = "法定代表人")
    @Size(max = 100)
    private String legalRepresentative;
    @Schema(description = "成立日期")
    @Size(max = 30)
    private String establishDate;
    @Schema(description = "经营状态")
    @Size(max = 100)
    private String businessStatus;
    @Schema(description = "注册资本")
    @Size(max = 100)
    private String registeredCapital;
    @Schema(description = "注册地址")
    @Size(max = 500)
    private String registeredAddress;
    @Schema(description = "所属地区")
    @Size(max = 255)
    private String region;
    @Schema(description = "统一社会信用代码")
    @Size(max = 100)
    private String unifiedSocialCreditCode;
    @Schema(description = "所属行业")
    @Size(max = 255)
    private String industry;
    @Schema(description = "经营范围")
    @Size(max = 4000)
    private String businessScope;
    @Schema(description = "企业简介")
    @Size(max = 4000)
    private String introduction;
}
