package cn.iocoder.txgy.module.trade.dal.dataobject.checkout;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 平台结算正式化准备记录。
 *
 * <p>该记录仅保存服务端对不可变拆单规划的 readiness 审计，不是订单、支付或库存事实。</p>
 */
@TableName("trade_platform_checkout_formalization")
@KeySequence("trade_platform_checkout_formalization_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PlatformCheckoutFormalizationDO extends BaseDO {

    @TableId
    private Long id;
    private Long checkoutId;
    private String checkoutNo;
    private Long platformMemberId;
    private String idempotencyKey;
    private String snapshotDigest;
    private String attemptStatus;
    private String blockerCodes;
    private String blockerReason;
    private Long version;
}
