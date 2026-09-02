package cn.iocoder.txgy.module.fms.controller.admin.voucher.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * FMS 凭证科目发生额 VO
 *
 * @author OPENLAB BS
 */
@Data
public class FmsVoucherSubjectAmountVO {

    /**
     * 科目编号
     */
    private Long subjectId;
    /**
     * 借方发生额
     */
    private BigDecimal debitAmount;
    /**
     * 贷方发生额
     */
    private BigDecimal creditAmount;

}
