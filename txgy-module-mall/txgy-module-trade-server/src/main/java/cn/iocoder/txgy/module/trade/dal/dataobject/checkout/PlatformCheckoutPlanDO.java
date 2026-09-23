package cn.iocoder.txgy.module.trade.dal.dataobject.checkout;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 尚未生成真实订单的平台结算租户/店铺拆单规划快照。 */
@TableName("trade_platform_checkout_plan")
@KeySequence("trade_platform_checkout_plan_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PlatformCheckoutPlanDO extends BaseDO {

    @TableId
    private Long id;
    private Long checkoutId;
    private String checkoutNo;
    private String planNo;
    private Long tenantId;
    private Long sellerEnterpriseId;
    private Long shopId;
    private String tenantName;
    private String enterpriseName;
    private String shopName;
    private Integer itemCount;
    private Integer totalQuantity;
    private Long goodsAmount;
    private Long freightAmount;
    private Long discountAmount;
    private Long payAmount;
    private String planStatus;
    private String blockReason;
    private Long version;
}
