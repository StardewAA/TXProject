package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * 证照台账 Excel 导入行（五页改造缺接口契约 Q1，2026-09-28）。
 *
 * <p>规范出处：Q1 契约——POST /bidding/certificate/import（Excel、行级回执、模板下载）。
 * 列口径对齐 CertificateSaveReqVO 建档必填：证照名称/证书编号/证照大类/持证主体。
 * <p>为什么这样写：证照大类取 v3.3 §二 固定 code（ADMIN_LICENSE/SYSTEM_CERT/PERSON_QUAL/HONOR/OTHER），
 * 枚举属业务字典而非硬编码阈值——模板示例行给出参考值，Excel 下拉由前端按字典渲染。
 */
@Data
public class CertificateImportExcelVO {

    @ExcelProperty("证照名称")
    private String certName;

    @ExcelProperty("证书编号")
    private String certNo;

    @ExcelProperty("证照大类 code（ADMIN_LICENSE/SYSTEM_CERT/PERSON_QUAL/HONOR/OTHER）")
    private String certType;

    @ExcelProperty("持证主体")
    private String holderName;

    @ExcelProperty("资质等级")
    private String certLevel;

    @ExcelProperty("许可/业务范围")
    private String certScope;

    @ExcelProperty("颁发机构")
    private String issuingAuthority;

    @ExcelProperty("颁发机构统一社会信用代码")
    private String issuingAuthorityCode;

    @ExcelProperty("颁发日期")
    private LocalDate issueDate;

    @ExcelProperty("有效期起始")
    private LocalDate validFrom;

    @ExcelProperty("有效期止（留空=长期）")
    private LocalDate validTo;

    @ExcelProperty("备注")
    private String remark;
}
