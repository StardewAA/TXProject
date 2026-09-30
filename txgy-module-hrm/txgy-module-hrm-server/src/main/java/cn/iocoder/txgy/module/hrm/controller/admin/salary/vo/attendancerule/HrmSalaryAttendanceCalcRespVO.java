package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 考勤计薪试算 Response VO
 *
 * <p>把"缺勤扣多少、满勤奖多少、最终应发多少"一次算清楚，可直接用于月度工资表。</p>
 */
@Schema(description = "管理后台 - 考勤计薪试算 Response VO")
@Data
public class HrmSalaryAttendanceCalcRespVO {

    @Schema(description = "使用的规则编号", example = "1")
    private Long ruleId;
    @Schema(description = "使用的规则名称", example = "标准计薪规则")
    private String ruleName;
    @Schema(description = "满勤天数", example = "26")
    private BigDecimal fullAttendanceDays;
    @Schema(description = "日薪", example = "307.69")
    private BigDecimal dailyWage;
    @Schema(description = "折算后的缺勤天数（含迟到早退折算）", example = "1.33")
    private BigDecimal absentDaysTotal;
    @Schema(description = "缺勤扣款", example = "409.23")
    private BigDecimal absenceDeductAmount;
    @Schema(description = "事假扣款", example = "307.69")
    private BigDecimal personalLeaveDeductAmount;
    @Schema(description = "病假扣款", example = "0.00")
    private BigDecimal sickLeaveDeductAmount;
    @Schema(description = "扣款合计", example = "716.92")
    private BigDecimal totalDeductAmount;
    @Schema(description = "满勤奖", example = "0.00")
    private BigDecimal fullAttendanceBonusAmount;
    @Schema(description = "是否满勤", example = "false")
    private Boolean fullAttendance;
    @Schema(description = "试算应发（基数 − 扣款合计 + 满勤奖）", example = "7283.08")
    private BigDecimal payableSalary;
    @Schema(description = "说明", example = "满勤 26 天；缺勤 1 天按 100% 日薪扣；迟到早退 3 次折算 1 天")
    private String remark;

}
