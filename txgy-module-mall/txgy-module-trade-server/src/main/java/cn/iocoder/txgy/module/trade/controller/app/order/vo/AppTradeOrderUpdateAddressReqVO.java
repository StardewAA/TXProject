package cn.iocoder.txgy.module.trade.controller.app.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Schema(description = "用户 App - 待支付订单修改收货地址 Request VO")
@Data
public class AppTradeOrderUpdateAddressReqVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "订单编号不能为空")
    @Positive(message = "订单编号不正确")
    private Long id;

    @Schema(description = "本人地址簿中的收货地址编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "收货地址不能为空")
    @Positive(message = "收货地址不正确")
    private Long addressId;
}
