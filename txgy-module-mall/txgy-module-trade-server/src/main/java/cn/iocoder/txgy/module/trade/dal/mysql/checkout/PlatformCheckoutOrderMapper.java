package cn.iocoder.txgy.module.trade.dal.mysql.checkout;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlatformCheckoutOrderMapper extends BaseMapperX<PlatformCheckoutOrderDO> {

    default PlatformCheckoutOrderDO selectByOrder(Long orderId) {
        return TenantUtils.executeIgnore(() -> selectOne(new LambdaQueryWrapperX<PlatformCheckoutOrderDO>()
                .eq(PlatformCheckoutOrderDO::getOrderId, orderId)));
    }

    default List<PlatformCheckoutOrderDO> selectListByCheckout(Long checkoutId, String checkoutNo) {
        return TenantUtils.executeIgnore(() -> selectListWithCurrentStatus(checkoutId, checkoutNo));
    }

    @Select("""
            SELECT r.id, r.checkout_id, r.checkout_no, r.order_id, r.order_no, r.tenant_id,
                   r.buyer_member_id, r.buyer_enterprise_id, r.seller_enterprise_id, r.shop_id,
                   r.goods_amount, r.freight_amount, r.discount_amount, r.pay_amount,
                   r.refunded_amount, COALESCE(o.status, r.order_status) AS order_status,
                   r.after_sale_status, r.creator, r.create_time, r.updater, r.update_time, r.deleted
            FROM trade_platform_checkout_order r
            LEFT JOIN trade_order o
              ON o.id = r.order_id AND o.tenant_id = r.tenant_id AND o.deleted = 0
            WHERE r.checkout_id = #{checkoutId}
              AND r.checkout_no = #{checkoutNo}
              AND r.deleted = 0
            ORDER BY r.tenant_id, r.shop_id, r.id
            """)
    List<PlatformCheckoutOrderDO> selectListWithCurrentStatus(@Param("checkoutId") Long checkoutId,
                                                               @Param("checkoutNo") String checkoutNo);

}
