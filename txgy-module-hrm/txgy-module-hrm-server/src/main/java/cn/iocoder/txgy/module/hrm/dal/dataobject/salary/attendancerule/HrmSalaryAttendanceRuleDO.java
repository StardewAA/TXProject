package cn.iocoder.txgy.module.hrm.dal.dataobject.salary.attendancerule;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * HRM 考勤计薪规则 DO
 *
 * <p>把"出勤/缺勤怎么算钱"做成可配置模板：满勤天数、日薪折算方式、缺勤扣款比例、
 * 满勤奖比例、病假/事假扣款比例、迟到早退折算，全部由 HR 在页面上填，不写死在代码里。</p>
 *
 * @author OPENLAB BS
 */
@TableName("hrm_salary_attendance_rule")
@KeySequence("hrm_salary_attendance_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrmSalaryAttendanceRuleDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 规则名称
     */
    private String name;
    /**
     * 满勤天数（当月应出勤天数）
     */
    private BigDecimal fullAttendanceDays;
    /**
     * 日薪折算方式：1 月薪 ÷ 满勤天数，2 按日薪直接取
     */
    private Integer dailyWageMode;
    /**
     * 缺勤扣款比例（%，0-200）
     */
    private BigDecimal absenceDeductRate;
    /**
     * 满勤奖比例（%，按日薪计算，0 表示无满勤奖）
     */
    private BigDecimal fullAttendanceBonusRate;
    /**
     * 事假扣款比例（%）
     */
    private BigDecimal personalLeaveRate;
    /**
     * 病假扣款比例（%）
     */
    private BigDecimal sickLeaveRate;
    /**
     * 迟到早退几次折算 1 天缺勤（0 表示不折算）
     */
    private Integer lateToAbsenceTimes;
    /**
     * 是否默认规则
     */
    private Boolean defaultFlag;
    /**
     * 状态：0 启用，1 停用
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;

}
