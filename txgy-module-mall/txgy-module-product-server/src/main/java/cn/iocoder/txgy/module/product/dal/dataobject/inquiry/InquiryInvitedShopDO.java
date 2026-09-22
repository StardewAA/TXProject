package cn.iocoder.txgy.module.product.dal.dataobject.inquiry;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 定向询价允许参与的供应店铺。 */
@TableName("product_purchase_inquiry_invited_shop")
@Data
@EqualsAndHashCode(callSuper = true)
public class InquiryInvitedShopDO extends TenantBaseDO {
    @TableId private Long id;
    private Long inquiryId;
    private Long shopId;
}
