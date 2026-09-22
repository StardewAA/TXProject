package cn.iocoder.txgy.module.product.dal.dataobject.inquiry;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("product_purchase_inquiry_quote_message")
@Data
@EqualsAndHashCode(callSuper = true)
public class InquiryQuoteMessageDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long quoteId;
    private Long senderMemberId;
    /** 后台供应商消息的实际 system_user；采购会员消息时为空。 */
    private Long senderAdminUserId;
    /** BUYER 或 SUPPLIER。 */
    private String senderRole;
    private String clientMessageId;
    private String content;
}
