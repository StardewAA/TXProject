package cn.iocoder.txgy.module.trade.service.order.handler;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.module.trade.service.erp.TradeErpInventoryService;
import cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.Operation;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * 商品 SKU 库存的 {@link TradeOrderHandler} 实现类
 *
 * @author OPENLAB BS
 */
@Component
public class TradeProductSkuOrderHandler implements TradeOrderHandler {

    @Resource
    private TradeErpInventoryService erpInventory;

    @Override
    public void afterOrderCreate(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        erpInventory.request(order, Operation.RESERVE, "ORDER", orderItems, null, null);
    }

    @Override
    public void afterCancelOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        // 售后的订单项，已经在 afterCancelOrderItem 回滚库存，所以这里不需要重复回滚
        orderItems = filterOrderItemListByNoneAfterSale(orderItems);
        if (CollUtil.isEmpty(orderItems)) {
            return;
        }
        erpInventory.request(order, Operation.RELEASE, "CANCEL", orderItems, null, null);
    }

    @Override
    public void afterCancelOrderItem(TradeOrderDO order, TradeOrderItemDO orderItem) {
        // 退款不是退货入库；仅未发货订单释放预占。入库在 ERP 确认实际收货后处理。
        if (order.getDeliveryTime() == null && (order.getStatus() == 0 || order.getStatus() == 10)) {
            erpInventory.request(order, Operation.RELEASE, "ITEM-" + orderItem.getId(),
                    singletonList(orderItem), null, null);
        }
    }

}
