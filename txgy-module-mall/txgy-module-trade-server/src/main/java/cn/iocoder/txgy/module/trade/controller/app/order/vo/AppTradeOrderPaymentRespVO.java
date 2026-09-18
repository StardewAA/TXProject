package cn.iocoder.txgy.module.trade.controller.app.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** PC/H5 订单列表与详情共用的支付窗口。前端倒计时不是支付授权。 */
@Data
public class AppTradeOrderPaymentRespVO {

    @Schema(description = "支付截止时间；ERP 等待中尚未开始支付计时")
    private LocalDateTime payExpireTime;
    @Schema(description = "ERP 等待截止时间")
    private LocalDateTime erpWaitExpireTime;
    @Schema(description = "支付截止的 Unix 毫秒时间戳")
    private Long payExpireTimestamp;
    @Schema(description = "ERP 等待截止的 Unix 毫秒时间戳")
    private Long erpWaitExpireTimestamp;
    @Schema(description = "服务端当前 Unix 毫秒时间戳")
    private Long serverTime;
    @Schema(description = "是否可进入支付；提交支付仍由支付服务再次核实")
    private Boolean canPay;
    @Schema(description = "不可支付原因，正常可付款时为空")
    private String paymentBlockReason;
    @Schema(description = "取消原因类型；50 为 ERP 等待超时")
    private Integer cancelType;
}
