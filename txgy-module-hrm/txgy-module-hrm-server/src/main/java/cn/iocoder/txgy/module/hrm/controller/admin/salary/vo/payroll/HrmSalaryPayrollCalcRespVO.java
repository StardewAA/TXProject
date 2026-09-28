package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payroll;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

/**
 * 薪酬计算引擎 Response VO
 *
 * <p>逐项对应租户 2020 的 10 个工资项编码（1001~9999），可直接写进工资表的 option_values。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 薪酬计算引擎 Response VO")
@Data
public class HrmSalaryPayrollCalcRespVO {

    @Schema(description = "员工编号", example = "131")
    private Long employeeId;
    @Schema(description = "员工姓名", example = "李保温")
    private String employeeName;
    @Schema(description = "年份", example = "2026")
    private Integer year;
    @Schema(description = "月份", example = "8")
    private Integer month;

    @Schema(description = "1001 基本工资", example = "8000.00")
    private BigDecimal baseSalary;
    @Schema(description = "1002 绩效工资", example = "2200.00")
    private BigDecimal performanceSalary;
    @Schema(description = "1003 满勤奖", example = "0.00")
    private BigDecimal fullAttendanceBonus;
    @Schema(description = "1004 加班/计件工资（预留，由 MES 计量导入）", example = "0.00")
    private BigDecimal pieceworkSalary;
    @Schema(description = "2001 缺勤扣款", example = "513.84")
    private BigDecimal absenceDeduct;
    @Schema(description = "2002 事假病假扣款", example = "307.69")
    private BigDecimal leaveDeduct;
    @Schema(description = "3001 代扣社保（个人）", example = "696.00")
    private BigDecimal personalInsurance;
    @Schema(description = "3002 代扣公积金（个人）", example = "240.00")
    private BigDecimal personalProvidentFund;

    @Schema(description = "应发合计（1001+1002+1003+1004）", example = "10200.00")
    private BigDecimal expectedPaySalary;
    @Schema(description = "税前扣除合计（2001+2002+3001+3002）", example = "1757.53")
    private BigDecimal preTaxDeduction;
    @Schema(description = "应税工资（应发 − 税前扣除，个税由工资表按税率表计算）", example = "8442.47")
    private BigDecimal taxableSalary;
    @Schema(description = "绩效系数（来源：绩效结果取数）", example = "1.10")
    private BigDecimal performanceCoefficient;
    @Schema(description = "绩效结果等级", example = "B")
    private String performanceLevel;
    @Schema(description = "数据来源说明")
    private String remark;
    @Schema(description = "工资项明细（编码 → 金额），可直接写入工资表 option_values", example = "{\"1001\":8000.00}")
    private LinkedHashMap<Integer, BigDecimal> optionValues;

}
