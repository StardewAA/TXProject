package cn.iocoder.txgy.module.hrm.controller.admin.salary;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.performancedata.HrmSalaryPerformanceRespVO;
import cn.iocoder.txgy.module.hrm.service.salary.performancedata.HrmSalaryPerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 绩效结果取数：月度工资表按绩效系数算绩效工资。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 绩效结果取数")
@RestController
@RequestMapping("/hrm/salary/performance-data")
@Validated
public class HrmSalaryPerformanceController {

    @Resource
    private HrmSalaryPerformanceService salaryPerformanceService;

    @GetMapping("/get")
    @Operation(summary = "获得员工最近一次已完成的绩效结果", description = "返回得分/等级/绩效系数，供工资表算绩效工资")
    @Parameter(name = "employeeId", description = "员工编号", required = true, example = "131")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<HrmSalaryPerformanceRespVO> getLatestPerformance(
            @RequestParam("employeeId") Long employeeId) {
        return success(salaryPerformanceService.getLatestPerformance(employeeId));
    }

    @GetMapping("/list-by-plan")
    @Operation(summary = "获得某考核计划下全部员工的绩效结果", description = "月度工资表批量取数用")
    @Parameter(name = "planId", description = "考核计划编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<List<HrmSalaryPerformanceRespVO>> getPerformanceListByPlan(
            @RequestParam("planId") Long planId) {
        return success(salaryPerformanceService.getPerformanceListByPlan(planId));
    }

}
