package cn.iocoder.txgy.module.product.dal.dataobject.enterprise;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 后台系统账号与商城企业的成员关系。 */
@TableName("product_enterprise_admin_member")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductEnterpriseAdminMemberDO extends TenantBaseDO {

    /** 仅显式的企业管理员可管理企业下全部店铺；普通员工仍须逐店授权。 */
    public static final int SCOPE_ENTERPRISE = 1;
    public static final int SCOPE_SHOP = 2;

    @TableId
    private Long id;
    private Long adminUserId;
    private Long enterpriseId;
    private Integer scopeType;
    /** 0 启用，1 停用。 */
    private Integer status;

}
