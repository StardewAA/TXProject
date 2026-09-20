package cn.iocoder.txgy.module.hrm.service.salary.payment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payment.HrmSalaryPaymentBatchRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeSalaryCardDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.salary.slip.HrmSalarySlipDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.salary.slip.HrmSalarySlipMapper;
import cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeSalaryCardService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HRM 工资银行代发 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmSalaryPaymentServiceImpl implements HrmSalaryPaymentService {

    @Resource
    private HrmSalarySlipMapper salarySlipMapper;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private HrmEmployeeSalaryCardService salaryCardService;
    @Resource
    private List<BankPaymentFileGenerator> paymentFileGenerators;

    @Override
    public HrmSalaryPaymentBatchRespVO previewBatch(Integer year, Integer month) {
        HrmSalaryPaymentBatchRespVO resp = new HrmSalaryPaymentBatchRespVO();
        resp.setYear(year);
        resp.setMonth(month);
        // 1. 取当月工资条（实发金额的来源）
        List<HrmSalarySlipDO> slips = salarySlipMapper.selectList(
                new LambdaQueryWrapperX<HrmSalarySlipDO>()
                        .eq(HrmSalarySlipDO::getYear, year)
                        .eq(HrmSalarySlipDO::getMonth, month));
        if (slips.isEmpty()) {
            resp.setEmployeeCount(0);
            resp.setTotalAmount(BigDecimal.ZERO);
            resp.setMissingCardCount(0);
            resp.setItems(List.of());
            return resp;
        }
        Set<Long> employeeIds = slips.stream().map(HrmSalarySlipDO::getEmployeeId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, HrmEmployeeDO> employeeMap = employeeService.getEmployeeMap(employeeIds);
        Map<Long, HrmEmployeeSalaryCardDO> cardMap = salaryCardService.getSalaryCardMap(employeeIds);
        // 2. 组装明细
        List<HrmSalaryPaymentBatchRespVO.PaymentItem> items = new ArrayList<>(slips.size());
        BigDecimal total = BigDecimal.ZERO;
        int missingCard = 0;
        for (HrmSalarySlipDO slip : slips) {
            HrmSalaryPaymentBatchRespVO.PaymentItem item = new HrmSalaryPaymentBatchRespVO.PaymentItem();
            item.setEmployeeId(slip.getEmployeeId());
            HrmEmployeeDO employee = slip.getEmployeeId() == null ? null : employeeMap.get(slip.getEmployeeId());
            if (employee != null) {
                item.setEmployeeName(employee.getName());
                item.setJobNumber(employee.getJobNumber());
            }
            HrmEmployeeSalaryCardDO card = slip.getEmployeeId() == null ? null : cardMap.get(slip.getEmployeeId());
            if (card == null || StrUtil.isBlank(card.getBankCardNumber())) {
                item.setPayable(false);
                item.setUnpayableReason("未登记工资卡");
                missingCard++;
            } else {
                item.setPayable(true);
                item.setBankName(card.getBankName());
                item.setBankBranchName(card.getBankBranchName());
                item.setBankCardNumber(card.getBankCardNumber());
            }
            BigDecimal amount = slip.getRealPaySalary() == null ? BigDecimal.ZERO : slip.getRealPaySalary();
            item.setAmount(amount);
            if (Boolean.TRUE.equals(item.getPayable())) {
                total = total.add(amount);
            }
            items.add(item);
        }
        resp.setItems(items);
        resp.setEmployeeCount(items.size());
        resp.setTotalAmount(total);
        resp.setMissingCardCount(missingCard);
        return resp;
    }

    @Override
    public String generatePaymentData(Integer year, Integer month, String bankCode) {
        HrmSalaryPaymentBatchRespVO batch = previewBatch(year, month);
        List<HrmSalaryPaymentBatchRespVO.PaymentItem> payableItems = batch.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getPayable()))
                .collect(Collectors.toList());
        String code = StrUtil.blankToDefault(bankCode, "generic-csv");
        // 找不到指定银行实现时，回退到通用 CSV，保证主流程不中断
        BankPaymentFileGenerator generator = paymentFileGenerators.stream()
                .filter(item -> code.equalsIgnoreCase(item.getBankCode()))
                .findFirst()
                .orElseGet(() -> paymentFileGenerators.stream()
                        .filter(item -> "generic-csv".equalsIgnoreCase(item.getBankCode()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("缺少通用代发文件生成器")));
        return generator.generate(year, month, payableItems);
    }

}
