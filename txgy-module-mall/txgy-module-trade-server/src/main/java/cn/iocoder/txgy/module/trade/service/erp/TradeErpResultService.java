package cn.iocoder.txgy.module.trade.service.erp;

import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import cn.iocoder.txgy.module.trade.service.aftersale.AfterSaleService;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.*;

@Service
public class TradeErpResultService {
    private final TradeErpInventoryService inventory;
    private final TradeOrderQueryService orders;
    private final TradeOrderUpdateService updates;
    private final AfterSaleService afterSales;
    private final AfterSaleMapper afterSaleMapper;

    public TradeErpResultService(TradeErpInventoryService inventory, TradeOrderQueryService orders,
                                TradeOrderUpdateService updates, AfterSaleService afterSales, AfterSaleMapper afterSaleMapper) {
        this.inventory=inventory; this.orders=orders; this.updates=updates;
        this.afterSales=afterSales; this.afterSaleMapper=afterSaleMapper;
    }

    /** 请求结果与商城状态在同一数据库事务中推进；不切换用户身份、不绕过租户。 */
    @Transactional(rollbackFor=Exception.class)
    public Entry receive(String secret, Callback callback) {
        inventory.authenticateCallback(secret);
        Entry previous = inventory.get(callback.requestId());
        Entry entry = inventory.confirm(callback);
        if (!entry.confirmed() || previous.confirmed()) return entry;
        var order = orders.getOrder(entry.orderId());
        if (order == null) throw TradeErpInventoryService.invalid("ERP 对应商城订单不存在");
        switch (Operation.valueOf(entry.operation())) {
            case RESERVE -> {
                if (order.getStatus() == 40) {
                    // 取消与预占回传交叉：保留事实，再申请释放，绝不把订单复活。
                    inventory.request(order, Operation.RELEASE, "LATE-" + entry.id(),
                            orders.getOrderItemListByOrderId(order.getId()), null, null);
                } else {
                    updates.continueAfterErpReservation(order.getId());
                }
            }
            case OUTBOUND -> {
                if (order.getStatus() != 10) throw TradeErpInventoryService.invalid("订单状态与ERP出库回传冲突，请人工核实");
                if ("DELIVERY".equals(entry.businessKey())) {
                    updates.deliveryOrder(new TradeOrderDeliveryReqVO().setId(order.getId())
                            .setLogisticsId(entry.logisticsId()).setLogisticsNo(entry.logisticsNo()));
                }
                // 自提出库确认不等于买家收货；仍需有权限的门店人员核销。
            }
            case RETURN -> {
                var sale = afterSaleMapper.selectById(Long.valueOf(entry.businessKey()));
                if (sale == null || !sale.getOrderId().equals(order.getId()) || sale.getStatus() != 30)
                    throw TradeErpInventoryService.invalid("售后状态与ERP入库回传冲突，请人工核实");
                afterSales.receiveAfterSale(null, sale.getId());
            }
            case RELEASE -> { /* 释放不改回已取消的订单，也不修改商品库存。 */ }
        }
        return entry;
    }
}
