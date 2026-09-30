package cn.iocoder.txgy.module.product.dal.dataobject.enterprise;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
@TableName("product_enterprise") @KeySequence("product_enterprise_seq") @Data @EqualsAndHashCode(callSuper = true) @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductEnterpriseDO extends TenantBaseDO { @TableId private Long id; private String name; private String creditCode; private Integer type; private String licenseUrl; private String contactName; private String contactMobile; private String address; private String introduction; private Integer auditStatus; private String auditRemark; }
