package cn.iocoder.txgy.module.product.dal.dataobject.enterprise;
import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;
/** 会员企业申请；营业执照不通过公开商品接口暴露。 */
@TableName("product_enterprise_application")
@Data @EqualsAndHashCode(callSuper = true)
public class EnterpriseApplicationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long memberId;
    private String identity;
    private String name;
    private String creditCode;
    private String licenseData;
    private String contactName;
    private String contactMobile;
    private String address;
    private String introduction;
    private Integer status;
    private String auditRemark;
    private Long auditorId;
    private LocalDateTime auditTime;
    private Long enterpriseId;
    /** 供应商审核通过后自动创建或绑定的企业店铺。 */
    private Long shopId;
    /** 供应商审核时显式关联的后台企业管理员账号。 */
    private Long adminUserId;
}
