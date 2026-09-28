package cn.iocoder.txgy.module.trade.controller.app.checkout.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 平台跨租户结算预览 Request VO")
@Data
public class AppPlatformCheckoutPreviewReqVO {

    @Schema(description = "平台购物车项编号；为空时使用当前全部已选中项")
    @Size(max = 200, message = "单次结算商品不能超过 200 项")
    private List<@Positive(message = "购物车项编号必须大于 0") Long> cartItemIds;

    @Schema(description = "平台账号的收货地址编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "收货地址不能为空")
    @Positive(message = "收货地址编号必须大于 0")
    private Long addressId;

}
