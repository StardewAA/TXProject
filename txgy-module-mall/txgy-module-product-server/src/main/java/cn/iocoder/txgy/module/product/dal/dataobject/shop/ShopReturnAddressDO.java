package cn.iocoder.txgy.module.product.dal.dataobject.shop;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

/** 私有退货资料，不混入公开店铺响应。 */
@TableName("product_shop_return_address")
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopReturnAddressDO extends TenantBaseDO {
    @TableId(type = IdType.INPUT)
    private Long shopId;
    private String receiverName;
    private String receiverMobile;
    private String address;
}
