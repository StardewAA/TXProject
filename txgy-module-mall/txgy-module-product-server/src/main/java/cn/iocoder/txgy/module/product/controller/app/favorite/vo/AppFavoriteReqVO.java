package cn.iocoder.txgy.module.product.controller.app.favorite.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "用户 APP - 商品收藏的单个 Request VO") // 用于收藏、取消收藏、获取收藏
@Data
public class AppFavoriteReqVO {

    @Schema(description = "商品 SPU 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "29502")
    @NotNull(message = "商品 SPU 编号不能为空")
    private Long spuId;

    @Schema(description = "平台门户租户标识；跨租户商品收藏时必传", example = "kedun")
    @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$", message = "租户标识格式不正确")
    private String tenantSlug;

}
