package cn.iocoder.txgy.module.pay.controller.admin.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 支付订单 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PayOrderRespVO extends PayOrderBaseVO {

    @Schema(description = "支付订单编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "服务端当前时间，毫秒时间戳；收银台用于校准倒计时")
    private Long serverTime;

    @Schema(description = "实际支付截止时间，毫秒时间戳")
    private Long expireTimestamp;

}
