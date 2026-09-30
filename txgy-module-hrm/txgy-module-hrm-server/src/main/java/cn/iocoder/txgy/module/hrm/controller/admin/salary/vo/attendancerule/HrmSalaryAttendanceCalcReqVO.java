package cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 考勤计薪试算 Request VO
 *
 * <p>当前采用"HR 传天数"的混合口径（考勤数据源尚不完整）；
 * 后续接入考勤自动统计时，只需把 days 的来源换成打卡/请假统计，规则与算法不变。</p>
 */
@Schema(description = "管理后台 - 考勤计薪试算 Request VO")
@Data
public class HrmSalaryAttendanceCalcReqVO {

    @Schema(description = "考勤计薪规则编号（不传则用默认规则）", example = "1")
    private Long ruleId;

    @Schema(description = "月薪/应发工资基数", requiredMode = Schema.RequiredMode.REQUIRED, example = "8000")
    @NotNull(message = "工资基数不能为空")
    private BigDecimal baseSalary;

    @Schema(description = "实际出勤天数", example = "24")
    private BigDecimal attendanceDays;

    @Schema(description = "缺勤天数（旷工/未出勤）", example = "1")
    private BigDecimal absentDays;

    @Schema(description = "事假天数", example = "1")
    private BigDecimal personalLeaveDays;

    @Schema(description = "病假天数", example = "0")
    private BigDecimal sickLeaveDays;

    @Schema(description = "迟到早退次数", example = "2")
    private Integer lateTimes;

}
