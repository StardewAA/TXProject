package cn.iocoder.txgy.module.hrm.controller.admin.company.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - HRM 公司信息（用工主体）保存 Request VO")
@Data
public class HrmCompanyProfileSaveReqVO {

    @Schema(description = "编号；为空表示新增", example = "1")
    private Long id;

    @Schema(description = "公司名称（用工主体）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "秦川化工产业园发展有限公司")
    @NotBlank(message = "公司名称不能为空")
    @Size(max = 128, message = "公司名称不能超过 128 个字符")
    private String name;

    @Schema(description = "统一社会信用代码", example = "91610100MA6XXXXXXX")
    @Size(max = 64, message = "统一社会信用代码不能超过 64 个字符")
    private String creditCode;

    @Schema(description = "住所", example = "陕西省西安市雁塔区XX路1号")
    @Size(max = 255, message = "住所不能超过 255 个字符")
    private String address;

    @Schema(description = "法定代表人", example = "张三")
    @Size(max = 64, message = "法定代表人不能超过 64 个字符")
    private String legalPerson;

    @Schema(description = "成立日期")
    private java.time.LocalDate establishDate;

    @Schema(description = "注册资本", example = "1000万元人民币")
    @Size(max = 64, message = "注册资本不能超过 64 个字符")
    private String registeredCapital;

    @Schema(description = "企业类型", example = "有限责任公司(自然人投资或控股)")
    @Size(max = 64, message = "企业类型不能超过 64 个字符")
    private String companyType;

    @Schema(description = "经营范围")
    @Size(max = 1000, message = "经营范围不能超过 1000 个字符")
    private String businessScope;

    @Schema(description = "参保人数", example = "128")
    private Integer insuredCount;

    @Schema(description = "联系电话", example = "029-88888888")
    @Size(max = 32, message = "联系电话不能超过 32 个字符")
    private String phone;

    @Schema(description = "开户银行", example = "中国建设银行西安高新支行")
    @Size(max = 128, message = "开户银行不能超过 128 个字符")
    private String bankName;

    @Schema(description = "银行账号", example = "6100 1234 5678 9000 000")
    @Size(max = 64, message = "银行账号不能超过 64 个字符")
    private String bankAccount;

    @Schema(description = "商业保险说明", example = "雇主责任险 80 万/人，团体意外险 30 万/人")
    @Size(max = 255, message = "商业保险说明不能超过 255 个字符")
    private String commercialInsurance;

    @Schema(description = "是否默认主体", example = "true")
    private Boolean defaultStatus;

    @Schema(description = "显示顺序", example = "0")
    private Integer sort;

    @Schema(description = "备注")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

}
