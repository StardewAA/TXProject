package cn.iocoder.txgy.module.hrm.service.salary.performancedata;

import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.performancedata.HrmSalaryPerformanceRespVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * HRM 绩效结果取数 Service 接口
 *
 * @author OPENLAB BS
 */
public interface HrmSalaryPerformanceService {

    /**
     * 取员工最近一次已完成的绩效结果（用于算绩效工资）
     */
    HrmSalaryPerformanceRespVO getLatestPerformance(Long employeeId);

    /**
     * 取某个考核计划下全部员工的绩效结果（月度工资表批量取数）
     */
    List<HrmSalaryPerformanceRespVO> getPerformanceListByPlan(Long planId);

    /**
     * 批量取多个员工最近一次已完成的绩效结果（月度工资表核算用，避免逐人查询）
     *
     * @param employeeIds 员工编号集合
     * @return 员工编号 → 最近一次已完成绩效结果；无考核记录的员工不在 Map 中
     */
    Map<Long, HrmSalaryPerformanceRespVO> getLatestPerformanceMap(Collection<Long> employeeIds);

}
