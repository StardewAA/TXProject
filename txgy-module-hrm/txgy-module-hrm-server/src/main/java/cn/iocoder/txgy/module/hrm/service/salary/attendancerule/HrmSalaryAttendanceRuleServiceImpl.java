package cn.iocoder.txgy.module.hrm.service.salary.attendancerule;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceCalcReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceCalcRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceRulePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceRuleSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.attendancerule.HrmSalaryAttendanceRuleDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.salary.attendancerule.HrmSalaryAttendanceRuleMapper;
import cn.iocoder.txgy.module.hrm.service.salary.config.HrmSalaryRateLimitService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.ATTENDANCE_RULE_NOT_EXISTS;

/**
 * HRM 考勤计薪规则 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmSalaryAttendanceRuleServiceImpl implements HrmSalaryAttendanceRuleService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Resource
    private HrmSalaryAttendanceRuleMapper attendanceRuleMapper;
    @Resource
    private HrmSalaryRateLimitService rateLimitService;

    @Override
    public Long createRule(HrmSalaryAttendanceRuleSaveReqVO createReqVO) {
        HrmSalaryAttendanceRuleDO rule = BeanUtils.toBean(createReqVO, HrmSalaryAttendanceRuleDO.class);
        rule.setId(null);
        applyDefaults(rule);
        validateRates(rule);
        // 默认规则唯一：设为默认时把其它规则的默认标志清掉
        if (Boolean.TRUE.equals(rule.getDefaultFlag())) {
            clearDefaultFlag();
        }
        attendanceRuleMapper.insert(rule);
        return rule.getId();
    }

    @Override
    public void updateRule(HrmSalaryAttendanceRuleSaveReqVO updateReqVO) {
        validateRuleExists(updateReqVO.getId());
        HrmSalaryAttendanceRuleDO rule = BeanUtils.toBean(updateReqVO, HrmSalaryAttendanceRuleDO.class);
        applyDefaults(rule);
        validateRates(rule);
        if (Boolean.TRUE.equals(rule.getDefaultFlag())) {
            clearDefaultFlag();
        }
        attendanceRuleMapper.updateById(rule);
    }

    /**
     * 比例上限校验：各项比例不得高于「薪资管理 → 比例上限」中该计薪项配置的上限。
     *
     * <p>上限由 HR 按项维护（不是写死 100%）；未配置时按 {@link HrmSalaryRateLimitService#DEFAULT_RATE_LIMIT} 兜底。</p>
     */
    private void validateRates(HrmSalaryAttendanceRuleDO rule) {
        rateLimitService.validateAttendanceRate("absenceDeductRate", "缺勤扣款比例", rule.getAbsenceDeductRate());
        rateLimitService.validateAttendanceRate("fullAttendanceBonusRate", "满勤奖比例", rule.getFullAttendanceBonusRate());
        rateLimitService.validateAttendanceRate("personalLeaveRate", "事假扣款比例", rule.getPersonalLeaveRate());
        rateLimitService.validateAttendanceRate("sickLeaveRate", "病假扣款比例", rule.getSickLeaveRate());
    }

    @Override
    public void deleteRule(Long id) {
        validateRuleExists(id);
        attendanceRuleMapper.deleteById(id);
    }

    @Override
    public HrmSalaryAttendanceRuleDO getRule(Long id) {
        return attendanceRuleMapper.selectById(id);
    }

    @Override
    public HrmSalaryAttendanceRuleDO validateRuleExists(Long id) {
        HrmSalaryAttendanceRuleDO rule = attendanceRuleMapper.selectById(id);
        if (rule == null) {
            throw exception(ATTENDANCE_RULE_NOT_EXISTS);
        }
        return rule;
    }

    @Override
    public PageResult<HrmSalaryAttendanceRuleDO> getRulePage(HrmSalaryAttendanceRulePageReqVO pageReqVO) {
        return attendanceRuleMapper.selectPage(pageReqVO);
    }

    @Override
    public HrmSalaryAttendanceRuleDO getDefaultRule() {
        HrmSalaryAttendanceRuleDO rule = attendanceRuleMapper.selectDefaultRule();
        return rule == null ? buildBuiltinDefault() : rule;
    }

    @Override
    public HrmSalaryAttendanceCalcRespVO calc(HrmSalaryAttendanceCalcReqVO reqVO) {
        HrmSalaryAttendanceRuleDO rule = reqVO.getRuleId() == null ? getDefaultRule()
                : validateRuleExists(reqVO.getRuleId());
        BigDecimal baseSalary = nvl(reqVO.getBaseSalary());
        BigDecimal fullDays = nvl(rule.getFullAttendanceDays()).compareTo(BigDecimal.ZERO) > 0
                ? rule.getFullAttendanceDays() : BigDecimal.valueOf(26);
        // 1. 日薪：月薪 ÷ 满勤天数
        BigDecimal dailyWage = baseSalary.divide(fullDays, 2, RoundingMode.HALF_UP);
        // 2. 迟到早退折算天数
        BigDecimal lateAbsentDays = BigDecimal.ZERO;
        int lateTimes = reqVO.getLateTimes() == null ? 0 : reqVO.getLateTimes();
        int lateToAbsence = rule.getLateToAbsenceTimes() == null ? 0 : rule.getLateToAbsenceTimes();
        if (lateToAbsence > 0 && lateTimes > 0) {
            // 迟到折算缺勤必须「满 N 次才折 1 天」：整除取整，避免迟到 1 次（阈值 3 次）就被折成 0.33 天
            lateAbsentDays = BigDecimal.valueOf(lateTimes / lateToAbsence);
        }
        BigDecimal absentDaysTotal = nvl(reqVO.getAbsentDays()).add(lateAbsentDays);
        // 3. 各类扣款
        BigDecimal absenceDeduct = dailyWage.multiply(absentDaysTotal)
                .multiply(rate(rule.getAbsenceDeductRate(), HUNDRED)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal personalDeduct = dailyWage.multiply(nvl(reqVO.getPersonalLeaveDays()))
                .multiply(rate(rule.getPersonalLeaveRate(), HUNDRED)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sickDeduct = dailyWage.multiply(nvl(reqVO.getSickLeaveDays()))
                .multiply(rate(rule.getSickLeaveRate(), HUNDRED)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDeduct = absenceDeduct.add(personalDeduct).add(sickDeduct);
        // 4. 满勤奖：无缺勤/事假/病假/迟到时按日薪 × 比例
        boolean fullAttendance = absentDaysTotal.compareTo(BigDecimal.ZERO) == 0
                && nvl(reqVO.getPersonalLeaveDays()).compareTo(BigDecimal.ZERO) == 0
                && nvl(reqVO.getSickLeaveDays()).compareTo(BigDecimal.ZERO) == 0
                && lateTimes == 0;
        BigDecimal bonus = fullAttendance
                ? dailyWage.multiply(rate(rule.getFullAttendanceBonusRate(), BigDecimal.ZERO))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        // 5. 组装结果
        HrmSalaryAttendanceCalcRespVO resp = new HrmSalaryAttendanceCalcRespVO();
        resp.setRuleId(rule.getId());
        resp.setRuleName(rule.getName());
        resp.setFullAttendanceDays(fullDays);
        resp.setDailyWage(dailyWage);
        resp.setAbsentDaysTotal(absentDaysTotal);
        resp.setAbsenceDeductAmount(absenceDeduct);
        resp.setPersonalLeaveDeductAmount(personalDeduct);
        resp.setSickLeaveDeductAmount(sickDeduct);
        resp.setTotalDeductAmount(totalDeduct);
        resp.setFullAttendanceBonusAmount(bonus);
        resp.setFullAttendance(fullAttendance);
        resp.setPayableSalary(baseSalary.subtract(totalDeduct).add(bonus).setScale(2, RoundingMode.HALF_UP));
        resp.setRemark(StrUtil.format("满勤 {} 天；日薪 {}；缺勤 {} 天按 {}% 日薪扣；迟到早退 {} 次折算 {} 天",
                fullDays, dailyWage, absentDaysTotal, nvl(rule.getAbsenceDeductRate()), lateTimes, lateAbsentDays));
        return resp;
    }

    /**
     * 内置默认规则（数据库没有配置时兜底，保证工资表生成不因缺规则失败）
     */
    private HrmSalaryAttendanceRuleDO buildBuiltinDefault() {
        return HrmSalaryAttendanceRuleDO.builder()
                .name("内置默认计薪规则")
                .fullAttendanceDays(BigDecimal.valueOf(26))
                .dailyWageMode(1)
                .absenceDeductRate(BigDecimal.valueOf(100))
                .fullAttendanceBonusRate(BigDecimal.ZERO)
                .personalLeaveRate(BigDecimal.valueOf(100))
                .sickLeaveRate(BigDecimal.valueOf(60))
                .lateToAbsenceTimes(0)
                .defaultFlag(true)
                .status(0)
                .build();
    }

    private void applyDefaults(HrmSalaryAttendanceRuleDO rule) {
        if (rule.getDailyWageMode() == null) {
            rule.setDailyWageMode(1);
        }
        if (rule.getAbsenceDeductRate() == null) {
            rule.setAbsenceDeductRate(BigDecimal.valueOf(100));
        }
        if (rule.getFullAttendanceBonusRate() == null) {
            rule.setFullAttendanceBonusRate(BigDecimal.ZERO);
        }
        if (rule.getPersonalLeaveRate() == null) {
            rule.setPersonalLeaveRate(BigDecimal.valueOf(100));
        }
        if (rule.getSickLeaveRate() == null) {
            rule.setSickLeaveRate(BigDecimal.valueOf(60));
        }
        if (rule.getLateToAbsenceTimes() == null) {
            rule.setLateToAbsenceTimes(0);
        }
        if (rule.getDefaultFlag() == null) {
            rule.setDefaultFlag(false);
        }
        if (rule.getStatus() == null) {
            rule.setStatus(0);
        }
    }

    private void clearDefaultFlag() {
        List<HrmSalaryAttendanceRuleDO> list = attendanceRuleMapper.selectList();
        for (HrmSalaryAttendanceRuleDO item : list) {
            if (Boolean.TRUE.equals(item.getDefaultFlag())
                    && !Objects.equals(item.getDefaultFlag(), false)) {
                attendanceRuleMapper.updateById(
                        new HrmSalaryAttendanceRuleDO().setId(item.getId()).setDefaultFlag(false));
            }
        }
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 比例换算：入参是百分数（如 100 表示 100%），base 为 100 时代表"全额"。
     * 未配置时返回 1（即全额）。
     */
    private static BigDecimal rate(BigDecimal percent, BigDecimal defaultPercent) {
        BigDecimal value = percent == null ? defaultPercent : percent;
        return value.divide(HUNDRED, 6, RoundingMode.HALF_UP);
    }

}
