package cn.iocoder.txgy.module.erp.dal.dataobject.stock;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售绩效归因行（只读查询投影）
 *
 * <p>回答「哪些是 ERP 的什么人销售的」：以库存流水的 source_user_id（责任人快照）为归因依据，
 * 按「销售员 × 产品」聚合销售出库与销售退货的数量与金额。</p>
 *
 * <p>归因依据是<b>流水发生时刻的快照</b>，因此订单销售员后续变更不影响历史业绩归属。</p>
 *
 * @author OPENLAB BS
 */
@Data
public class ErpSalesAttributionRowDO {

    /**
     * 销售员用户编号（来自 erp_stock_record.source_user_id）
     */
    private Long userId;
    /**
     * 产品编码
     */
    private String productCode;
    /**
     * 产品名称
     */
    private String productName;
    /**
     * 销售出库数量
     */
    private BigDecimal outCount;
    /**
     * 销售退货数量
     */
    private BigDecimal returnCount;
    /**
     * 净销售数量 = 出库 - 退货
     */
    private BigDecimal netCount;
    /**
     * 销售出库金额
     */
    private BigDecimal outAmount;
    /**
     * 销售退货金额
     */
    private BigDecimal returnAmount;
    /**
     * 净销售金额 = 出库金额 - 退货金额
     */
    private BigDecimal netAmount;

}
