package cn.iocoder.txgy.module.hrm.controller.admin.salary;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.insurancededuct.HrmSalaryInsuranceDeductRespVO;
import cn.iocoder.txgy.module.hrm.service.salary.insurancededuct.HrmSalaryInsuranceDeductService;
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
 * 社保代扣取数：月度工资表生成时把"个人社保 + 个人公积金"带成代扣项。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 社保代扣取数")
@RestController
@RequestMapping("/hrm/salary/insurance-deduct")
@Validated
public class HrmSalaryInsuranceDeductController {

    @Resource
    private HrmSalaryInsuranceDeductService insuranceDeductService;

    @GetMapping("/get")
    @Operation(summary = "获得单个员工的社保代扣金额")
    @Parameter(name = "employeeId", description = "员工编号", required = true, example = "131")
    @Parameter(name = "year", description = "年份", required = true, example = "2026")
    @Parameter(name = "month", description = "月份", required = true, example = "8")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<HrmSalaryInsuranceDeductRespVO> getDeduct(@RequestParam("employeeId") Long employeeId,
                                                                 @RequestParam(value = "year", required = false) Integer year,
                                                                 @RequestParam(value = "month", required = false) Integer month) {
        // 年/月缺省时取当前自然月（避免"缺参即 400"）
        java.time.LocalDate now = java.time.LocalDate.now();
        return success(insuranceDeductService.getDeduct(employeeId,
                year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month));
    }

    @GetMapping("/list-by-month")
    @Operation(summary = "获得某月全部员工的社保代扣金额", description = "月度工资表生成时批量取数，避免逐个查询")
    @Parameter(name = "year", description = "年份", required = true, example = "2026")
    @Parameter(name = "month", description = "月份", required = true, example = "8")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<List<HrmSalaryInsuranceDeductRespVO>> getDeductListByMonth(
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month) {
        java.time.LocalDate now = java.time.LocalDate.now();
        return success(insuranceDeductService.getDeductListByMonth(
                year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month));
    }

}
