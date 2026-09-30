package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工证书 Excel 导入行（契约 e2e P2：员工证书导入）。
 *
 * <p>[为什么] 证照（certificate）与企业资质（qualification）都有 import + 模板，
 * 员工证书缺该入口 —— 批量录入只能逐条手填，与"替代 Excel 登记"的既有目标相悖。
 *
 * <p>[列口径] 与 {@link PersonnelCertificateSaveReqVO} 同源；证书类型/学历层次填 code
 * （见列头括号内取值，或下拉字典 person_cert_type / edu_level）。
 * 持证员工填 {@code personId}（员工档案主键）——这也是 {@code createCertificate} 的必填项。
 */
@Schema(description = "管理后台 - 员工证书导入行")
@Data
public class PersonnelCertImportExcelVO {

    @ExcelProperty("持证员工 personId（员工档案主键）")
    private Long personId;

    @ExcelProperty("证书类型 code（REG_PRACTICE_CERT/SKILL_CERT/TITLE_CERT/SAFETY_ASSESS_CERT/EDUCATION_CERT/OTHER）")
    private String certType;

    @ExcelProperty("证书名称")
    private String certName;

    @ExcelProperty("证书编号（同人同类型唯一）")
    private String certNo;

    @ExcelProperty("等级（一级/二级；安管 A/B/C）")
    private String certLevel;

    @ExcelProperty("专业")
    private String major;

    @ExcelProperty("学历层次 code（BACHELOR/MASTER/DOCTOR 等，仅学历证书填）")
    private String eduLevel;

    @ExcelProperty("发证机关")
    private String issuingAuthority;

    @ExcelProperty("发证日期（yyyy-MM-dd）")
    private LocalDate issueDate;

    @ExcelProperty("有效期起始（yyyy-MM-dd）")
    private LocalDate validFrom;

    @ExcelProperty("有效期止（yyyy-MM-dd；留空=长期）")
    private LocalDate validTo;

    @ExcelProperty("备注")
    private String remark;
}
