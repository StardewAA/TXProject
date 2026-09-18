package cn.iocoder.txgy.module.product.dal.dataobject.inquiry;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@TableName("product_purchase_inquiry")
@Data @EqualsAndHashCode(callSuper=true)
public class InquiryDO extends TenantBaseDO {
 @TableId private Long id;
 private Long memberId;
 private Long enterpriseId;
 private String requestKey;
 private String no;
 private String title;
 private String contactName;
 private String contactMobile;
 private String address;
 private String remark;
 private Long deadline;
 private Long expectedDate;
 private Integer status;
 private String erpStatus;
 private String erpNo;
}
