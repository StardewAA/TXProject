package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderShopRespVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从订单项生成店铺视图，不重复分摊优惠或保存可失效的金额副本。 */
public final class TradeOrderShopSplitter {
    private TradeOrderShopSplitter() {
    }

    public static List<TradeOrderShopRespVO> split(List<TradeOrderItemDO> items) {
        Map<Long, TradeOrderShopRespVO> groups = new LinkedHashMap<>();
        for (TradeOrderItemDO item : items) {
            long shopId = item.getShopId() == null ? 0L : item.getShopId();
            TradeOrderShopRespVO group = groups.computeIfAbsent(shopId, id -> new TradeOrderShopRespVO()
                    .setShopId(id).setItemIds(new ArrayList<>()).setProductCount(0)
                    .setTotalPrice(0).setDeliveryPrice(0).setPayPrice(0));
            if (group.getShopName() == null && item.getShopName() != null) {
                group.setShopName(item.getShopName());
            }
            group.getItemIds().add(item.getId());
            int count = item.getCount() == null ? 0 : item.getCount();
            int price = item.getPrice() == null ? 0 : item.getPrice();
            group.setProductCount(Math.addExact(group.getProductCount(), count));
            group.setTotalPrice(Math.addExact(group.getTotalPrice(), Math.multiplyExact(price, count)));
            group.setDeliveryPrice(Math.addExact(group.getDeliveryPrice(), item.getDeliveryPrice() == null ? 0 : item.getDeliveryPrice()));
            group.setPayPrice(Math.addExact(group.getPayPrice(), item.getPayPrice() == null ? 0 : item.getPayPrice()));
        }
        return new ArrayList<>(groups.values());
    }
}
