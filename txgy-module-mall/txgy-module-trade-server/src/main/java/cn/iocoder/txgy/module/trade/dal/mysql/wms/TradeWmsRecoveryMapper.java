package cn.iocoder.txgy.module.trade.dal.mysql.wms;

import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * WMS 出库补偿查询。
 *
 * <p>租户条件同时约束订单和出库记录，避免定时任务在租户轮询时读到其它租户的数据。</p>
 */
@Mapper
public interface TradeWmsRecoveryMapper {

    @Select("""
            SELECT o.id, o.no, o.shop_id, o.pay_time
            FROM trade_order o
            WHERE o.tenant_id = #{tenantId}
              AND o.deleted = 0
              AND o.pay_status = TRUE
              AND o.status = 10
              AND NOT EXISTS (
                  SELECT 1
                  FROM trade_wms_shipment s
                  WHERE s.tenant_id = o.tenant_id
                    AND s.order_id = o.id
                    AND s.deleted = 0
              )
            ORDER BY o.id ASC
            LIMIT #{limit}
            """)
    List<TradeOrderDO> selectPaidOrdersWithoutShipment(@Param("tenantId") Long tenantId,
                                                        @Param("limit") int limit);
}
