package cn.iocoder.txgy.module.hrm.service.salary.payroll;

import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payroll.HrmSalaryPayrollCalcReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payroll.HrmSalaryPayrollCalcRespVO;

/**
 * HRM 薪酬计算引擎 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmSalaryPayrollService {

    /**
     * 计算单个员工的工资明细（考勤 + 社保 + 绩效 三合一）
     *
     * @param reqVO 计算参数
     * @return 工资明细
     */
    HrmSalaryPayrollCalcRespVO calcPayroll(HrmSalaryPayrollCalcReqVO reqVO);

}
