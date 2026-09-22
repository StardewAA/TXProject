package cn.iocoder.txgy.module.trade.dal.dataobject.checkout;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/** 平台跨租户结算单。 */
@TableName("trade_platform_checkout")
@KeySequence("trade_platform_checkout_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PlatformCheckoutDO extends BaseDO {

    @TableId
    private Long id;
    private String checkoutNo;
    private String idempotencyKey;
    private String requestDigest;
    private Long platformMemberId;
    private Long addressId;
    private Long addressSourceTenantId;
    private String receiverName;
    private String receiverMobile;
    private Long receiverAreaId;
    private String receiverDetailAddress;
    private BigDecimal receiverLongitude;
    private BigDecimal receiverLatitude;
    private String receiverCoordSystem;
    /** 下单备注快照；规划草稿也必须保留幂等请求的完整业务含义。 */
    private String remark;
    private String currency;
    private Long goodsAmount;
    private Long freightAmount;
    private Long discountAmount;
    private Long payAmount;
    private Long paidAmount;
    private Long refundedAmount;
    private String payStatus;
    private String aggregateStatus;
    private String failureReason;
    private Long platformPayOrderId;
    private String platformPayOrderNo;
    private Long version;

}
