package cn.iocoder.txgy.module.trade.dal.dataobject.cart;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 平台级跨租户购物车。 */
@TableName("trade_platform_cart")
@KeySequence("trade_platform_cart_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PlatformCartDO extends BaseDO {

    @TableId
    private Long id;
    private Long platformMemberId;
    private Long tenantId;
    private Long enterpriseId;
    private Long shopId;
    private Long spuId;
    private Long skuId;
    private Integer quantity;
    private Boolean selected;
    private String invalidReason;

}
