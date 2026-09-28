package cn.iocoder.txgy.module.product.dal.dataobject.inquiry;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("product_purchase_inquiry_quote_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class InquiryQuoteItemDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long quoteId;
    private Long inquiryItemId;
    private Long skuId;
    /** 保存报价时的不可变商品身份快照。 */
    private String productName;
    private String skuName;
    private String skuSpecification;
    private Long spuContentVersion;
    private Integer count;
    /** 单价，单位：分。 */
    private Integer unitPrice;
    /** 小计，单位：分。 */
    private Long totalPrice;
}
