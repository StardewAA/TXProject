package cn.iocoder.txgy.module.trade.controller.app.checkout.vo;

import cn.iocoder.txgy.module.product.api.property.dto.ProductPropertyValueDetailRespDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 平台跨租户结算预览 Response VO")
@Data
public class AppPlatformCheckoutPreviewRespVO {

    private Long platformAccountId;
    private String currency;
    /** 通过服务端实时校验的购物车行数。 */
    private Integer validItemCount;
    private Integer totalQuantity;
    /** 单位：分。 */
    private Long goodsAmount;
    /** 单位：分；任一店铺运费无法核定时为 null，绝不以 0 表示未知。 */
    private Long freightAmount;
    private Long discountAmount;
    /** 商品金额与已核定运费之和；运费未就绪时为 null。 */
    private Long payAmount;
    /** true 表示所有租户、店铺均已按当前地址从服务端运费模板核定。 */
    private Boolean freightReady;
    private Boolean validationPassed;
    /** 当前为 false，防止把商品金额预览误当成可支付结算结果。 */
    private Boolean submittable;
    private AppPlatformCheckoutAddressRespVO address;
    private List<String> blockingReasons;
    private List<TenantGroup> tenantGroups;
    private List<FailedItem> failedItems;

    @Data
    public static class TenantGroup {
        private Long tenantId;
        private String tenantName;
        private Long goodsAmount;
        private Long freightAmount;
        private Boolean freightReady;
        private List<ShopGroup> shops;
    }

    @Data
    public static class ShopGroup {
        private Long enterpriseId;
        private String enterpriseName;
        private Long shopId;
        private String shopName;
        private Long goodsAmount;
        private Long freightAmount;
        private Boolean freightReady;
        private String freightFailureReason;
        private List<Item> items;
    }

    @Data
    public static class Item {
        private Long cartItemId;
        private Long tenantId;
        private Long enterpriseId;
        private Long shopId;
        private Long spuId;
        private String spuName;
        private Long skuId;
        private List<ProductPropertyValueDetailRespDTO> properties;
        private String picUrl;
        private Integer quantity;
        private Integer unitPrice;
        private Long goodsAmount;
    }

    @Data
    public static class FailedItem {
        private Long cartItemId;
        private Long skuId;
        private String reason;
    }

}
