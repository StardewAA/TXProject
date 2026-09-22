package cn.iocoder.txgy.module.product.dal.dataobject.shop;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 后台系统账号的店铺经营授权。 */
@TableName("product_shop_admin_authorization")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductShopAdminAuthorizationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long adminUserId;
    private Long enterpriseId;
    private Long shopId;
    /** 0 启用，1 停用。 */
    private Integer status;

}
