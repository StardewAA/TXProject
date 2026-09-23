package cn.iocoder.txgy.module.trade.controller.app.cart.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "用户 App - 更新平台购物车数量 Request VO")
@Data
public class AppPlatformCartUpdateCountReqVO {

    @NotNull(message = "购物车项编号不能为空")
    @Min(value = 1, message = "购物车项编号无效")
    private Long id;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "商品数量必须大于等于 1")
    @Max(value = 99999, message = "单个规格最多购买 99999 件")
    private Integer quantity;

}
