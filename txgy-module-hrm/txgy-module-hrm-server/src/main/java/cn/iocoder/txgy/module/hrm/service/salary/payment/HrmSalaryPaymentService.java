package cn.iocoder.txgy.module.hrm.service.salary.payment;

import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.payment.HrmSalaryPaymentBatchRespVO;

/**
 * HRM 工资银行代发 Service 接口（预留）
 *
 * <p>本期只提供"批次预览 + 代发数据生成"，不直连银行；
 * 需要直连时，在适配层新增实现即可。</p>
 *
 * @author OPENLAB BS
 */
public interface HrmSalaryPaymentService {

    /**
     * 预览某月代发批次（按工资条实发金额 + 员工工资卡组装）
     *
     * @param year  年份
     * @param month 月份
     * @return 代发批次
     */
    HrmSalaryPaymentBatchRespVO previewBatch(Integer year, Integer month);

    /**
     * 生成代发数据（默认通用 CSV；传 bankCode 可切换适配层实现）
     *
     * @param year     年份
     * @param month    月份
     * @param bankCode 银行编码，默认 generic-csv
     * @return 文件内容
     */
    String generatePaymentData(Integer year, Integer month, String bankCode);

}
