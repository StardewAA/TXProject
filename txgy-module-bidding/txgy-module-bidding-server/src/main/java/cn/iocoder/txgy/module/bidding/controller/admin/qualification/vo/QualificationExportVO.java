package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * 企业资质导出 Excel 行（五页改造缺接口契约 Q6，2026-09-28；范围=当前筛选）。
 */
@Data
public class QualificationExportVO {

    @ExcelProperty("ID")
    private Long id;

    @ExcelProperty("资质序列")
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

    @ExcelProperty("有效期止")
    private LocalDate validTo;

    @ExcelProperty("资质状态")
    private String qualStatus;

    @ExcelProperty("关联人员数")
    private Long staffCount;
}
