package cn.iocoder.txgy.module.hrm.controller.admin.salary;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payroll.HrmSalaryPayrollCalcReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payroll.HrmSalaryPayrollCalcRespVO;
import cn.iocoder.txgy.module.hrm.service.salary.payroll.HrmSalaryPayrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 薪酬计算引擎：考勤 + 社保 + 绩效 三合一，输出可直接写入工资表的工资项明细。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 薪酬计算引擎")
@RestController
@RequestMapping("/hrm/salary/payroll")
@Validated
public class HrmSalaryPayrollController {

    @Resource
    private HrmSalaryPayrollService salaryPayrollService;

    @PostMapping("/calc")
    @Operation(summary = "计算员工工资明细",
            description = "自动取考勤（满勤奖/缺勤扣款）、社保（个人社保/公积金）、绩效（基数×系数），返回工资项明细与应发/应税")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<HrmSalaryPayrollCalcRespVO> calcPayroll(
            @Valid @RequestBody HrmSalaryPayrollCalcReqVO calcReqVO) {
        return success(salaryPayrollService.calcPayroll(calcReqVO));
    }

}
