package cn.iocoder.txgy.module.trade.dal.dataobject.checkout;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 平台结算单关联的单租户、单店铺子订单。 */
@TableName("trade_platform_checkout_order")
@KeySequence("trade_platform_checkout_order_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PlatformCheckoutOrderDO extends BaseDO {

    @TableId
    private Long id;
    private Long checkoutId;
    private String checkoutNo;
    private Long orderId;
    private String orderNo;
    private Long tenantId;
    private Long buyerMemberId;
    private Long buyerEnterpriseId;
    private Long sellerEnterpriseId;
    private Long shopId;
    private Long goodsAmount;
    private Long freightAmount;
    private Long discountAmount;
    private Long payAmount;
    private Long refundedAmount;
    private Integer orderStatus;
    private Integer afterSaleStatus;

}
