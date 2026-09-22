package cn.iocoder.txgy.module.trade.dal.mysql.wms;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.trade.dal.dataobject.wms.TradeWmsShipmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Collection;

@Mapper
public interface TradeWmsShipmentMapper extends BaseMapperX<TradeWmsShipmentDO> {

    default TradeWmsShipmentDO selectByGroup(Long orderId, Long shopId, Long warehouseId) {
        return selectOne(new LambdaQueryWrapperX<TradeWmsShipmentDO>()
                .eq(TradeWmsShipmentDO::getOrderId, orderId)
                .eq(TradeWmsShipmentDO::getShopId, shopId)
                .eq(TradeWmsShipmentDO::getWarehouseId, warehouseId));
    }

    default TradeWmsShipmentDO selectBySourceOrderNo(String sourceOrderNo) {
        return selectOne(TradeWmsShipmentDO::getSourceOrderNo, sourceOrderNo);
    }

    default List<TradeWmsShipmentDO> selectListByOrderId(Long orderId) {
        return selectList(new LambdaQueryWrapperX<TradeWmsShipmentDO>()
                .eq(TradeWmsShipmentDO::getOrderId, orderId)
                .orderByAsc(TradeWmsShipmentDO::getShopId, TradeWmsShipmentDO::getWarehouseId));
    }

    default List<TradeWmsShipmentDO> selectListByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<TradeWmsShipmentDO>()
                .in(TradeWmsShipmentDO::getOrderId, orderIds)
                .orderByAsc(TradeWmsShipmentDO::getOrderId, TradeWmsShipmentDO::getShopId,
                        TradeWmsShipmentDO::getWarehouseId));
    }

    default List<TradeWmsShipmentDO> selectRetryable(int limit) {
        return selectList(new LambdaQueryWrapperX<TradeWmsShipmentDO>()
                .in(TradeWmsShipmentDO::getStatus, "WMS_OUTBOUND_PENDING", "WMS_UNAVAILABLE", "WMS_OUTBOUND_REJECTED")
                .orderByAsc(TradeWmsShipmentDO::getUpdateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }
}
