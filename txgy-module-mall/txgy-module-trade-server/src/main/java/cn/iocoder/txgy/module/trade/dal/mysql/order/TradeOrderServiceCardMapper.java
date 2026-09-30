package cn.iocoder.txgy.module.trade.dal.mysql.order;

import cn.iocoder.txgy.module.trade.api.order.dto.TradeOrderServiceCardRespDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Explicit tenant/member projection: no receiver, payment credential or address fields. */
@Mapper
public interface TradeOrderServiceCardMapper {
    @Select("SELECT id, tenant_id, user_id, shop_id, no, pay_price, status FROM trade_order "
            + "WHERE id=#{id} AND tenant_id=#{tenantId} AND user_id=#{userId} AND deleted=0")
    TradeOrderServiceCardRespDTO selectOwned(@Param("id") Long id, @Param("userId") Long userId,
                                            @Param("tenantId") Long tenantId);
}
