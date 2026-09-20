package cn.iocoder.txgy.module.hrm.service.salary.payment;

import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payment.HrmSalaryPaymentBatchRespVO;

import java.util.List;

/**
 * 银行代发文件适配层（预留接口）
 *
 * <p>每家银行代发文件的列顺序、分隔符、金额格式都不同。主流程只依赖本接口：
 * 拿到具体银行的模板后，新增一个实现类（如 {@code CcbPaymentFileGenerator}）即可，<b>不用改主流程</b>。</p>
 *
 * @author OPENLAB BS
 */
public interface BankPaymentFileGenerator {

    /**
     * 银行编码（唯一标识，按它选择实现类）
     *
     * @return 银行编码，例如 generic-csv / ccb / icbc
     */
    String getBankCode();

    /**
     * 生成代发文件内容
     *
     * @param year  年份
     * @param month 月份
     * @param items 代发明细（只包含可代发行）
     * @return 文件内容（CSV 文本 / 银行报文等）
     */
    String generate(Integer year, Integer month, List<HrmSalaryPaymentBatchRespVO.PaymentItem> items);

}
