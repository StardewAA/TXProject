package cn.iocoder.txgy.module.erp.dal.dataobject.stock;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售员维度的金额聚合行（只读查询投影）
 *
 * <p>被两个查询复用，SQL 中统一起别名为 {@code amount} / {@code docCount}：</p>
 * <ul>
 *     <li>销售订单金额：{@code erp_sale_order}（已审批）</li>
 *     <li>回款金额：{@code erp_finance_receipt} + {@code erp_finance_receipt_item}，经订单归因到销售员</li>
 * </ul>
 *
 * @author OPENLAB BS
 */
@Data
public class ErpSalesUserAmountRowDO {

    /**
     * 销售员用户编号
     */
    private Long userId;
    /**
     * 金额合计
     */
    private BigDecimal amount;
    /**
     * 单据数量
     */
    private Long docCount;

}
