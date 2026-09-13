package cn.iocoder.txgy.module.product.dal.dataobject.inquiry;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.math.BigDecimal;
@TableName("product_purchase_inquiry_item")
@Data @EqualsAndHashCode(callSuper=true)
public class InquiryItemDO extends TenantBaseDO {
 @TableId private Long id;
 private Long inquiryId;
 private Long skuId;
 private String picUrl;
 private String code;
 private String name;
 private String category;
 private String specification;
 private BigDecimal quantity;
 private String unit;
 private String remark;
}
