package cn.iocoder.txgy.module.hrm.service.salary.payroll;

import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceCalcReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.attendancerule.HrmSalaryAttendanceCalcRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.insurancededuct.HrmSalaryInsuranceDeductRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payroll.HrmSalaryPayrollCalcReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payroll.HrmSalaryPayrollCalcRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.performancedata.HrmSalaryPerformanceRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.enums.salary.config.HrmSalaryOptionCodeEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.salary.attendancerule.HrmSalaryAttendanceRuleService;
import cn.iocoder.txgy.module.hrm.service.salary.insurancededuct.HrmSalaryInsuranceDeductService;
import cn.iocoder.txgy.module.hrm.service.salary.performancedata.HrmSalaryPerformanceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;

/**
 * HRM 薪酬计算引擎 Service 实现类
 *
 * <p>计算口径：</p>
 * <ol>
 *     <li>考勤：调用考勤计薪规则，得到满勤奖、缺勤扣款、事假/病假扣款；</li>
 *     <li>社保：调用社保代扣取数，得到个人社保与个人公积金；</li>
 *     <li>绩效：调用绩效结果取数，按「绩效工资基数 × 绩效系数」算绩效工资；</li>
 *     <li>汇总：应发 = 收入项合计；应税 = 应发 − 税前扣除（个税留给工资表按税率表算）。</li>
 * </ol>
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmSalaryPayrollServiceImpl implements HrmSalaryPayrollService {

    @Resource
    private HrmSalaryAttendanceRuleService attendanceRuleService;
    @Resource
    private HrmSalaryInsuranceDeductService insuranceDeductService;
    @Resource
    private HrmSalaryPerformanceService performanceService;
    @Resource
    private HrmEmployeeService employeeService;

    @Override
    public HrmSalaryPayrollCalcRespVO calcPayroll(HrmSalaryPayrollCalcReqVO reqVO) {
        BigDecimal baseSalary = nvl(reqVO.getBaseSalary());
        // 1. 考勤计薪规则：满勤奖 / 缺勤扣款 / 事假病假扣款
        HrmSalaryAttendanceCalcReqVO attendanceReq = new HrmSalaryAttendanceCalcReqVO();
        attendanceReq.setBaseSalary(baseSalary);
        attendanceReq.setAbsentDays(reqVO.getAbsentDays());
        attendanceReq.setPersonalLeaveDays(reqVO.getPersonalLeaveDays());
        attendanceReq.setSickLeaveDays(reqVO.getSickLeaveDays());
        attendanceReq.setLateTimes(reqVO.getLateTimes());
        HrmSalaryAttendanceCalcRespVO attendance = attendanceRuleService.calc(attendanceReq);
        BigDecimal fullAttendanceBonus = nvl(attendance.getFullAttendanceBonusAmount());
        BigDecimal absenceDeduct = nvl(attendance.getAbsenceDeductAmount());
        BigDecimal leaveDeduct = nvl(attendance.getPersonalLeaveDeductAmount())
                .add(nvl(attendance.getSickLeaveDeductAmount()));
        // 2. 社保代扣：传了员工编号则自动取数，否则用入参
        BigDecimal personalInsurance = nvl(reqVO.getPersonalInsuranceAmount());
        BigDecimal personalFund = nvl(reqVO.getPersonalProvidentFundAmount());
        HrmEmployeeDO employee = null;
        if (reqVO.getEmployeeId() != null) {
            employee = employeeService.getEmployee(reqVO.getEmployeeId());
            if (reqVO.getYear() != null && reqVO.getMonth() != null) {
                HrmSalaryInsuranceDeductRespVO insurance = insuranceDeductService.getDeduct(
                        reqVO.getEmployeeId(), reqVO.getYear(), reqVO.getMonth());
                if (insurance != null && Boolean.TRUE.equals(insurance.getFound())) {
                    personalInsurance = nvl(insurance.getPersonalInsuranceAmount());
                    personalFund = nvl(insurance.getPersonalProvidentFundAmount());
                }
            }
        }
        // 3. 绩效：基数 × 系数（传了员工编号则取最近一次已完成绩效的系数）
        BigDecimal coefficient = reqVO.getPerformanceCoefficient() == null
                ? BigDecimal.ONE : reqVO.getPerformanceCoefficient();
        String level = null;
        if (reqVO.getEmployeeId() != null) {
            HrmSalaryPerformanceRespVO performance =
                    performanceService.getLatestPerformance(reqVO.getEmployeeId());
            if (performance != null && Boolean.TRUE.equals(performance.getFound())
                    && performance.getCoefficient() != null) {
                coefficient = performance.getCoefficient();
                level = performance.getResultLevel();
            }
        }
        // 未填绩效基数时按 0 处理（口径：只有明确填了绩效基数才算绩效工资）。
        // 此前用「基本工资」兜底，会让计件工、日结工这类无绩效岗位被多算一份绩效工资
        BigDecimal performanceBase = nvl(reqVO.getPerformanceBaseSalary());
        BigDecimal performanceSalary = performanceBase.multiply(coefficient)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal pieceworkSalary = BigDecimal.ZERO;
        // 4. 汇总
        BigDecimal expectedPay = baseSalary.add(performanceSalary)
                .add(fullAttendanceBonus).add(pieceworkSalary).setScale(2, RoundingMode.HALF_UP);
        BigDecimal preTaxDeduction = absenceDeduct.add(leaveDeduct)
                .add(personalInsurance).add(personalFund).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxable = expectedPay.subtract(preTaxDeduction).setScale(2, RoundingMode.HALF_UP);
        // 5. 组装（工资项编码对齐系统标准薪资项目录）
        HrmSalaryPayrollCalcRespVO resp = new HrmSalaryPayrollCalcRespVO();
        resp.setEmployeeId(reqVO.getEmployeeId());
        resp.setEmployeeName(employee == null ? null : employee.getName());
        resp.setYear(reqVO.getYear());
        resp.setMonth(reqVO.getMonth());
        resp.setBaseSalary(baseSalary);
        resp.setPerformanceSalary(performanceSalary);
        resp.setFullAttendanceBonus(fullAttendanceBonus);
        resp.setPieceworkSalary(pieceworkSalary);
        resp.setAbsenceDeduct(absenceDeduct);
        resp.setLeaveDeduct(leaveDeduct);
        resp.setPersonalInsurance(personalInsurance);
        resp.setPersonalProvidentFund(personalFund);
        resp.setExpectedPaySalary(expectedPay);
        resp.setPreTaxDeduction(preTaxDeduction);
        resp.setTaxableSalary(taxable);
        resp.setPerformanceCoefficient(coefficient);
        resp.setPerformanceLevel(level);
        LinkedHashMap<Integer, BigDecimal> optionValues = new LinkedHashMap<>();
        optionValues.put(HrmSalaryOptionCodeEnum.BASIC_SALARY.getCode(), baseSalary);
        optionValues.put(HrmSalaryOptionCodeEnum.PERFORMANCE_SALARY.getCode(), performanceSalary);
        optionValues.put(HrmSalaryOptionCodeEnum.FULL_ATTENDANCE_BONUS.getCode(), fullAttendanceBonus);
        optionValues.put(HrmSalaryOptionCodeEnum.PIECEWORK_SALARY.getCode(), pieceworkSalary);
        optionValues.put(HrmSalaryOptionCodeEnum.ABSENTEEISM_DEDUCTION.getCode(), absenceDeduct);
        optionValues.put(HrmSalaryOptionCodeEnum.LEAVE_DEDUCTION.getCode(), leaveDeduct);
        optionValues.put(HrmSalaryOptionCodeEnum.PERSONAL_INSURANCE.getCode(), personalInsurance);
        optionValues.put(HrmSalaryOptionCodeEnum.PERSONAL_PROVIDENT_FUND.getCode(), personalFund);
        resp.setOptionValues(optionValues);
        resp.setRemark("考勤：" + attendance.getRemark() + "；绩效系数 " + coefficient
                + (level == null ? "" : "（等级 " + level + "）") + "；个税需由工资表按税率表计算");
        return resp;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

}
