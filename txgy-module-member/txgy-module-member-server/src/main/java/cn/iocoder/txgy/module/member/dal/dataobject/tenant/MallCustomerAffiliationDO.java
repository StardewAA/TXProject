package cn.iocoder.txgy.module.member.dal.dataobject.tenant;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Customer service ownership only. Never used by enterprise/shop permission checks. */
@TableName("member_mall_customer_affiliation")
@Data
@EqualsAndHashCode(callSuper = true)
public class MallCustomerAffiliationDO extends TenantBaseDO {
    @TableId private Long id;
    private Long memberId;
    private Long serviceEnterpriseId;
    private String source;
}
