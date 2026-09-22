package cn.iocoder.txgy.module.trade.service.order.handler;

import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsOutboundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品 SKU 库存的 {@link TradeOrderHandler} 实现类
 *
 * @author OPENLAB BS
 */
@Component
public class TradeProductSkuOrderHandler implements TradeOrderHandler {

    private TradeWmsOutboundService outboundService;

    /** 仅供不触发支付回调的旧单元测试直接构造。 */
    public TradeProductSkuOrderHandler() {
    }

    @Autowired
    public TradeProductSkuOrderHandler(TradeWmsOutboundService outboundService) {
        this.outboundService = outboundService;
    }

    @Override
    public void afterOrderCreate(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        // 下单服务已在写入订单前重新读取 WMS；此处不重复查询，也不执行预占。
    }

    @Override
    public void afterPayOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (outboundService == null) {
            throw new IllegalStateException("WMS 出库申请服务未注入");
        }
        outboundService.createForPaidOrder(order, orderItems);
    }

    @Override
    public void afterCancelOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        // WMS 尚未提供预占，因此取消订单时没有可释放的 WMS 库存。
    }

    @Override
    public void afterCancelOrderItem(TradeOrderDO order, TradeOrderItemDO orderItem) {
        // WMS 尚未提供预占，因此退款时不发送虚假的释放请求。
    }

}
