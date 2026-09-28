package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * 企业资质 Excel 导入行（五页改造缺接口契约 Q6，2026-09-28）。
 *
 * <p>规范出处：Q6 契约——资质导入/导出，权限 bidding:qualification:import/export。
 * 列口径对齐 EnterpriseQualificationSaveReqVO 必填（qualSerial/qualCategory/qualGrade/certNo）；
 * entId 由后端按"一租户一主体档案"推导，模板不含企业列。
 */
@Data
public class QualificationImportExcelVO {

    @ExcelProperty("资质序列 code")
    private String qualSerial;

    @ExcelProperty("资质类别")
    private String qualCategory;

    @ExcelProperty("资质等级")
    private String qualGrade;

    @ExcelProperty("资质证书编号")
    private String certNo;

    @ExcelProperty("发证/许可机关")
    private String issuingAuthority;

    @ExcelProperty("发证日期")
    private LocalDate issueDate;

    @ExcelProperty("有效期止（留空=长期）")
    private LocalDate validTo;

    @ExcelProperty("批准承包/承揽范围")
    private String approvalScope;

    @ExcelProperty("备注")
    private String remark;
}
