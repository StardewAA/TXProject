package cn.iocoder.txgy.module.hrm.service.salary.payment;

import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payment.HrmSalaryPaymentBatchRespVO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用 CSV 银行代发文件生成器（默认实现）
 *
 * <p>列顺序：序号,开户银行,开户支行,银行卡号,户名,金额(元),用途,备注。
 * 拿到具体银行模板后，替换/新增实现即可，不影响主流程。</p>
 *
 * @author OPENLAB BS
 */
@Component
public class GenericCsvBankPaymentFileGenerator implements BankPaymentFileGenerator {

    @Override
    public String getBankCode() {
        return "generic-csv";
    }

    @Override
    public String generate(Integer year, Integer month, List<HrmSalaryPaymentBatchRespVO.PaymentItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("序号,开户银行,开户支行,银行卡号,户名,金额(元),用途,备注\n");
        int index = 1;
        for (HrmSalaryPaymentBatchRespVO.PaymentItem item : items) {
            sb.append(index++).append(',')
                    .append(blank(item.getBankName())).append(',')
                    .append(blank(item.getBankBranchName())).append(',')
                    .append(blank(item.getBankCardNumber())).append(',')
                    .append(blank(item.getEmployeeName())).append(',')
                    .append(item.getAmount() == null ? "0.00" : item.getAmount().toPlainString()).append(',')
                    .append(year).append('年').append(month).append("月工资").append(',')
                    .append("代发工资").append('\n');
        }
        return sb.toString();
    }

    private String blank(String value) {
        return value == null ? "" : value.replace(",", " ");
    }

}
