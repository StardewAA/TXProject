package cn.iocoder.txgy.module.trade.dal.dataobject.checkout;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 平台结算资金分摊台账。 */
@TableName("trade_platform_fund_allocation")
@KeySequence("trade_platform_fund_allocation_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PlatformFundAllocationDO extends BaseDO {

    @TableId
    private Long id;
    private Long checkoutId;
    private String checkoutNo;
    private Long orderId;
    private Long tenantId;
    private Long shopId;
    private Long allocatedAmount;
    private Long refundedAmount;
    private String allocationStatus;
    private Long version;

}
