package cn.iocoder.txgy.module.trade.controller.app.checkout.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "用户 App - 本人平台跨租户结算记录分页项 Response VO")
@Data
public class AppPlatformCheckoutPageItemRespVO {

    private String checkoutNo;
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
    private Boolean payable;
    private Boolean planningOnly;
    private LocalDateTime createTime;

}
