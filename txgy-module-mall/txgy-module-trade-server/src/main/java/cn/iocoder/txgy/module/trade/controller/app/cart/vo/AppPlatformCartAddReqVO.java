package cn.iocoder.txgy.module.trade.controller.app.cart.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "用户 App - 添加平台购物车商品 Request VO")
@Data
public class AppPlatformCartAddReqVO {

    @NotNull(message = "商品 SKU 编号不能为空")
    @Min(value = 1, message = "商品 SKU 编号无效")
    private Long skuId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "商品数量必须大于等于 1")
    @Max(value = 99999, message = "单个规格最多购买 99999 件")
    private Integer quantity;

}
