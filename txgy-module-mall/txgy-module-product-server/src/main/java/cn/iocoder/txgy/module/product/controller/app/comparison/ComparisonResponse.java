package cn.iocoder.txgy.module.product.controller.app.comparison;

import lombok.Data;
import java.util.List;

@Data
public class ComparisonResponse {
    private Integer quantity;
    private String preference;
    private String quantityUnit = "SALE_UNIT";
    private long calculatedAt;
    private String strategyVersion = "comparison-v1";
    private String recommendationStatus;
    private String recommendationReason;
    private Long recommendedSkuId;
    private List<Item> items;
    private List<String> warnings;

    public record Property(String name, String value) {}
    @Data
    public static class Item {
        private Long skuId;
        private boolean available;
        private String unavailableReason;
        private Long spuId;
        private String spuName;
        private String picUrl;
        private Long shopId;
        private String shopName;
        /** Current public SKU catalogue price in cents, not a checkout quote. */
        private Integer price;
        private List<Property> properties = List.of();
        private String standardStatus = "UNKNOWN";
        private String standardMessage = "缺少经核验的标准规格、计量单位和包装换算，暂不能认定同款或推荐替代";
        private String stockStatus = "PENDING_ERP";
        private String stockMessage = "库存与交期以下单时ERP履约确认为准，比价不预占库存";
        private List<String> unknowns = List.of();
        private List<String> matchedRequirements = List.of();
        private List<String> differences = List.of();
    }
}
