package cn.iocoder.txgy.module.hrm.controller.admin.company.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - HRM 公司信息（用工主体）Response VO")
@Data
public class HrmCompanyProfileRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "公司名称（用工主体）")
    private String name;

    @Schema(description = "统一社会信用代码")
    private String creditCode;

    @Schema(description = "住所")
    private String address;

    @Schema(description = "法定代表人")
    private String legalPerson;

    @Schema(description = "成立日期")
    private java.time.LocalDate establishDate;

    @Schema(description = "注册资本")
    private String registeredCapital;

    @Schema(description = "企业类型")
    private String companyType;

    @Schema(description = "经营范围")
    private String businessScope;

    @Schema(description = "参保人数")
    private Integer insuredCount;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "开户银行")
    private String bankName;

    @Schema(description = "银行账号")
    private String bankAccount;

    @Schema(description = "商业保险说明")
    private String commercialInsurance;

    @Schema(description = "是否默认主体")
    private Boolean defaultStatus;

    @Schema(description = "显示顺序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
