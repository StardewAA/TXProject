package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;

/** Copy trusted member API values once. The order must never re-query the live address for display. */
public final class TradeOrderAddressSnapshot {
    private TradeOrderAddressSnapshot() { }
    public static void copy(MemberAddressRespDTO address, TradeOrderDO order) {
        order.setReceiverLongitude(address.getLongitude()).setReceiverLatitude(address.getLatitude())
                .setReceiverCoordSystem(address.getCoordSystem()).setReceiverLocationSource(address.getLocationSource())
                .setReceiverLocationPrecision(address.getLocationPrecision())
                .setReceiverLocationConfirmedAt(address.getLocationConfirmedAt());
    }
}
