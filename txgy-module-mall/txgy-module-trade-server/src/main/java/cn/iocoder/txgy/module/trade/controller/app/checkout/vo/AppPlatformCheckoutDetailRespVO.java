package cn.iocoder.txgy.module.trade.controller.app.checkout.vo;

import cn.iocoder.txgy.module.product.api.property.dto.ProductPropertyValueDetailRespDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "用户 App - 平台跨租户结算单详情 Response VO")
@Data
public class AppPlatformCheckoutDetailRespVO {

    private String checkoutNo;
    private String currency;
    private Long goodsAmount;
    private Long freightAmount;
    private Long discountAmount;
    private Long payAmount;
    private Long paidAmount;
    private Long refundedAmount;
    private String payStatus;
    private String aggregateStatus;
    private String failureReason;
    private String remark;
    private Long platformPayOrderId;
    private String platformPayOrderNo;
    private AppPlatformCheckoutAddressRespVO address;
    /** 聚合支付尚未接通前始终为 false。 */
    private Boolean payable;
    /** true 表示仅固化了不可支付的拆单规划，尚未生成业务订单。 */
    private Boolean planningOnly;
    private LocalDateTime createTime;
    private List<ChildOrder> childOrders;
    private List<FundAllocation> fundAllocations;
    private List<ChildPlan> childPlans;

    @Data
    public static class ChildPlan {
        private String planNo;
        private Long tenantId;
        private Long sellerEnterpriseId;
        private Long shopId;
        private String tenantName;
        private String enterpriseName;
        private String shopName;
        private Integer itemCount;
        private Integer totalQuantity;
        private Long goodsAmount;
        private Long freightAmount;
        private Long discountAmount;
        private Long payAmount;
        private String planStatus;
        private String blockReason;
        private List<PlanItem> items;
    }

    @Data
    public static class PlanItem {
        private Long cartItemId;
        private Long tenantId;
        private Long enterpriseId;
        private Long shopId;
        private Long spuId;
        private Long skuId;
        private String spuName;
        private List<ProductPropertyValueDetailRespDTO> properties;
        private String picUrl;
        private Integer quantity;
        private Integer unitPrice;
        private Long goodsAmount;
    }

    @Data
    public static class ChildOrder {
        private Long orderId;
        private String orderNo;
        private Long tenantId;
        private Long buyerMemberId;
        private Long buyerEnterpriseId;
        private Long sellerEnterpriseId;
        private Long shopId;
        private Long goodsAmount;
        private Long freightAmount;
        private Long discountAmount;
        private Long payAmount;
        private Long refundedAmount;
        private Integer orderStatus;
        private Integer afterSaleStatus;
    }

    @Data
    public static class FundAllocation {
        private Long orderId;
        private Long tenantId;
        private Long shopId;
        private Long allocatedAmount;
        private Long refundedAmount;
        private String allocationStatus;
    }

}
