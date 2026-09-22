package cn.iocoder.txgy.module.trade.dal.dataobject.checkout;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 平台结算拆单规划中的商品、价格和经营归属不可变快照。 */
@TableName("trade_platform_checkout_plan_item")
@KeySequence("trade_platform_checkout_plan_item_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PlatformCheckoutPlanItemDO extends BaseDO {

    @TableId
    private Long id;
    private Long checkoutId;
    private String checkoutNo;
    private Long planId;
    private String planNo;
    private Long cartItemId;
    private Long tenantId;
    private Long enterpriseId;
    private Long shopId;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String propertiesJson;
    private String picUrl;
    private Integer quantity;
    private Integer unitPrice;
    private Long goodsAmount;
}
