package cn.iocoder.txgy.module.trade.service.settlement;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public class SettlementModels {
    public record Generate(@NotNull @Positive Long shopId, @NotBlank @Pattern(regexp="\\d{4}-\\d{2}") String month,
                           @Min(0) @Max(10000) int commissionRate) {}
    public record Audit(@NotNull @Positive Long id, @Min(1) int version, boolean approved,
                        @NotBlank @Size(max=500) String remark) {}
    public record Confirm(@NotNull @Positive Long shopId, @NotNull @Positive Long id, @Min(1) int version) {}
    public record Pay(@NotNull @Positive Long id, @Min(1) int version, @NotNull @Positive Long amount,
                      @NotBlank @Size(max=100) String reference, @NotNull LocalDateTime paidAt,
                      @NotBlank @Size(max=2800000) String proof) {}
    @Data public static class Bill {
        private Long id, tenantId, shopId;
        private String shopName, billMonth, no;
        private Integer version, status, commissionRate, orderCount;
        private Long orderPayAmount, refundAmount, commissionAmount, adjustmentAmount, carryAmount, settlementAmount;
        private String auditRemark, paymentReference;
        private LocalDateTime paymentTime, createTime, updateTime;
    }
    @Data public static class Item {
        private Long id, tenantId, billId, orderId, originalOrderId, originalBillId;
        private String orderNo;
        private Integer kind, commissionRate;
        private Long payAmount, deliveryAmount, refundBefore, refundTotal, commissionAmount, amount;
        private LocalDateTime finishTime;
    }
}
