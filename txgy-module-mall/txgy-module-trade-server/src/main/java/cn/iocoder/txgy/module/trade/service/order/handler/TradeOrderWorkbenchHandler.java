package cn.iocoder.txgy.module.trade.service.order.handler;

import cn.iocoder.txgy.module.trade.dal.dataobject.order.*;
import cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchJournal;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class TradeOrderWorkbenchHandler implements TradeOrderHandler {
    private final OrderWorkbenchJournal journal;
    public TradeOrderWorkbenchHandler(OrderWorkbenchJournal journal){this.journal=journal;}
    @Override public void afterOrderCreate(TradeOrderDO order,List<TradeOrderItemDO> items){journal.changed(order.getId());}
    @Override public void afterPayOrder(TradeOrderDO order,List<TradeOrderItemDO> items){journal.changed(order.getId());}
    @Override public void afterCancelOrder(TradeOrderDO order,List<TradeOrderItemDO> items){journal.changed(order.getId());}
    @Override public void afterCancelOrderItem(TradeOrderDO order,TradeOrderItemDO item){journal.changed(order.getId());}
    @Override public void afterDeliveryOrder(TradeOrderDO order){journal.changed(order.getId());}
    @Override public void afterReceiveOrder(TradeOrderDO order){journal.changed(order.getId());}
}
