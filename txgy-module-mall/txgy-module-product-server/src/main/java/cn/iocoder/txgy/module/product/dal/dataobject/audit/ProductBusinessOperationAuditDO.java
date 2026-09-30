package cn.iocoder.txgy.module.product.dal.dataobject.audit;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商城企业经营关键操作审计。审计记录只追加，不参与业务状态计算。
 */
@TableName("product_business_operation_audit")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductBusinessOperationAuditDO extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enterpriseId;
    private Long shopId;
    /** MEMBER / ADMIN / SYSTEM。 */
    private String actorType;
    private Long actorId;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String result;
    private String detail;
}
