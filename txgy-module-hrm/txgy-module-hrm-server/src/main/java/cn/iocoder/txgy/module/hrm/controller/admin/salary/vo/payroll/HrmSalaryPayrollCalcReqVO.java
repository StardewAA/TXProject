package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payroll;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 薪酬计算引擎 Request VO
 *
 * <p>把三条取数（考勤计薪规则 / 社保代扣 / 绩效结果）合成一份工资明细：
 * 输入基本工资与出勤情况，输出各工资项金额，供月度工资表直接写入 option_values。</p>
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 薪酬计算引擎 Request VO")
@Data
public class HrmSalaryPayrollCalcReqVO {

    @Schema(description = "员工编号（传了则自动取社保代扣与绩效系数）", example = "131")
    private Long employeeId;

    @Schema(description = "年份（传员工编号时用于取社保）", example = "2026")
    private Integer year;

    @Schema(description = "月份（传员工编号时用于取社保）", example = "8")
    private Integer month;

    @Schema(description = "基本工资/应发基数", requiredMode = Schema.RequiredMode.REQUIRED, example = "8000")
    @NotNull(message = "基本工资不能为空")
    private BigDecimal baseSalary;

    @Schema(description = "绩效工资基数（不传则默认取基本工资）", example = "2000")
    private BigDecimal performanceBaseSalary;

    @Schema(description = "缺勤天数", example = "1")
    private BigDecimal absentDays;

    @Schema(description = "事假天数", example = "1")
    private BigDecimal personalLeaveDays;

    @Schema(description = "病假天数", example = "0")
    private BigDecimal sickLeaveDays;

    @Schema(description = "迟到早退次数", example = "2")
    private Integer lateTimes;

    @Schema(description = "代扣社保（个人）；传了员工编号则自动取数覆盖", example = "696.00")
    private BigDecimal personalInsuranceAmount;

    @Schema(description = "代扣公积金（个人）；传了员工编号则自动取数覆盖", example = "240.00")
    private BigDecimal personalProvidentFundAmount;

    @Schema(description = "绩效系数；传了员工编号则自动取数覆盖", example = "1.10")
    private BigDecimal performanceCoefficient;

}
