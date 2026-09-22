package cn.iocoder.txgy.module.product.dal.dataobject.inquiry;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 跨租户定向询价白名单；本表本身不承载询价正文。 */
@TableName("product_purchase_inquiry_invited_scope")
@Data
@EqualsAndHashCode(callSuper = true)
public class InquiryInvitedScopeDO extends BaseDO {
    @TableId
    private Long id;
    private Long inquiryId;
    private String targetType;
    private Long targetTenantId;
    private Long targetEnterpriseId;
    private Long targetShopId;
}
