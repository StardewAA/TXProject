package cn.iocoder.txgy.module.trade.controller.app.cart.vo;

import cn.iocoder.txgy.module.product.api.property.dto.ProductPropertyValueDetailRespDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 平台跨租户购物车 Response VO")
@Data
public class AppPlatformCartListRespVO {

    private Integer itemCount;
    private Integer totalQuantity;
    private Integer selectedQuantity;
    private List<TenantGroup> tenantGroups;

    @Data
    public static class TenantGroup {
        private Long tenantId;
        private String tenantName;
        private List<ShopGroup> shops;
    }

    @Data
    public static class ShopGroup {
        private Long enterpriseId;
        private String enterpriseName;
        private Long shopId;
        private String shopName;
        private List<Item> items;
    }

    @Data
    public static class Item {
        private Long id;
        private Long tenantId;
        private Long enterpriseId;
        private Long shopId;
        private Long spuId;
        private String spuName;
        private Long skuId;
        private List<ProductPropertyValueDetailRespDTO> properties;
        private String picUrl;
        /** 当前展示价，单位：分；成交价仍须在结算提交时重新计算。 */
        private Integer price;
        private Integer quantity;
        private Boolean selected;
        private Boolean valid;
        private String invalidReason;
    }

}
