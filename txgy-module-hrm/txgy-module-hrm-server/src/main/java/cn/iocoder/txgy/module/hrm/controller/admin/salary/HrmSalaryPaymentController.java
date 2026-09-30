package cn.iocoder.txgy.module.hrm.controller.admin.salary;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payment.HrmSalaryPaymentBatchRespVO;
import cn.iocoder.txgy.module.hrm.service.salary.payment.HrmSalaryPaymentService;
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

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 工资银行代发（预留接口）：本期只做批次预览与代发数据生成，不直连银行。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 工资银行代发（预留）")
@RestController
@RequestMapping("/hrm/salary/payment")
@Validated
public class HrmSalaryPaymentController {

    @Resource
    private HrmSalaryPaymentService salaryPaymentService;

    @GetMapping("/preview")
    @Operation(summary = "预览某月银行代发批次", description = "按工资条实发金额 + 员工工资卡组装，标记缺工资卡的人员")
    @Parameter(name = "year", description = "年份", required = true, example = "2026")
    @Parameter(name = "month", description = "月份", required = true, example = "8")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<HrmSalaryPaymentBatchRespVO> preview(@RequestParam(value = "year", required = false) Integer year,
                                                            @RequestParam(value = "month", required = false) Integer month) {
        // 年/月缺省时取当前自然月（银行代发一般针对当月，避免"缺参即 400"）
        java.time.LocalDate now = java.time.LocalDate.now();
        return success(salaryPaymentService.previewBatch(
                year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month));
    }

    @GetMapping("/generate")
    @Operation(summary = "生成银行代发数据", description = "默认通用 CSV；传 bankCode 可切换适配层实现（如具体银行模板）")
    @Parameter(name = "year", description = "年份", required = true, example = "2026")
    @Parameter(name = "month", description = "月份", required = true, example = "8")
    @Parameter(name = "bankCode", description = "银行编码，默认 generic-csv", example = "generic-csv")
    @PreAuthorize("@ss.hasPermission('hrm:salary:attendance-rule:query')")
    public CommonResult<String> generate(@RequestParam(value = "year", required = false) Integer year,
                                         @RequestParam(value = "month", required = false) Integer month,
                                         @RequestParam(value = "bankCode", required = false) String bankCode) {
        java.time.LocalDate now = java.time.LocalDate.now();
        return success(salaryPaymentService.generatePaymentData(
                year == null ? now.getYear() : year, month == null ? now.getMonthValue() : month, bankCode));
    }

}
