package cn.iocoder.txgy.module.trade.controller.app.checkout.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 平台跨租户结算提交 Request VO")
@Data
public class AppPlatformCheckoutSubmitReqVO {

    @Schema(description = "本次明确选中的平台购物车项编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请选择需要结算的平台购物车商品")
    @Size(max = 200, message = "单次结算商品不能超过 200 项")
    private List<@Positive(message = "购物车项编号必须大于 0") Long> cartItemIds;

    @Schema(description = "平台账号收货地址编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "收货地址不能为空")
    @Positive(message = "收货地址编号必须大于 0")
    private Long addressId;

    @Schema(description = "客户端生成的幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "幂等键不能为空")
    @Size(min = 8, max = 64, message = "幂等键长度必须为 8 到 64 个字符")
    @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "幂等键格式不正确")
    private String idempotencyKey;

    @Schema(description = "订单备注")
    @Size(max = 200, message = "订单备注不能超过 200 个字符")
    private String remark;

}
