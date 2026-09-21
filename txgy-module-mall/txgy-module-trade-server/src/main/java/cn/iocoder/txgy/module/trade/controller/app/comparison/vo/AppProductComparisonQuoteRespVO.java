package cn.iocoder.txgy.module.trade.controller.app.comparison.vo;

import lombok.Data;
import java.util.List;

/** Private per-member, per-SKU checkout estimate. Never a public offer, stock reservation or order. */
@Data
public class AppProductComparisonQuoteRespVO {
    private Integer quantity;
    private Long addressId;
    private long calculatedAt;
    private String priceBasis = "CHECKOUT_ESTIMATE";
    private List<String> warnings = List.of(
            "按本人所选地址及当前数量，对每个 SKU 分别独立结算预估，不是合并购买价格。",
            "包含当前适用的普通活动及会员优惠，不使用优惠券或积分；再次加购、下单时重新核价。",
            "库存尚未经 ERP 预占，不承诺配送时效；金额估算不代表不同商品的单位、包装或规格可比。");
    private List<Item> items;

    @Data
    public static class Item {
        private Long skuId;
        private Integer quantity;
        private String status;
        private String message;
        private boolean amountsComplete;
        /** All money in integer fen. Null means unknown, never free. */
        private Integer unitPrice;
        private Long goodsAmount;
        private Long discountAmount;
        private Long shippingFee;
        private Long totalAmount;
    }
}
