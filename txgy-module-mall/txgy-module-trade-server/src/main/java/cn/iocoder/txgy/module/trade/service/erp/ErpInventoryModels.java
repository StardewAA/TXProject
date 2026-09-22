package cn.iocoder.txgy.module.trade.service.erp;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ErpInventoryModels {
    private ErpInventoryModels() {}
    public enum Operation { RESERVE, RELEASE, OUTBOUND, RETURN }
    public record Item(Long skuId, int count, Long wmsSkuId, Long wmsWarehouseId) {}
    public record Entry(String id, Long orderId, String orderNo, String operation, String businessKey,
                        String mode, String status, String message, int attempts, String erpNo,
                        Long logisticsId, String logisticsNo, List<Item> items, LocalDateTime updateTime) {
        public boolean confirmed() { return "CONFIRMED".equals(status); }
    }
    public record Seed(@NotNull @Positive Long skuId, @Min(0) @Max(100000000) int quantity) {}
    public record Callback(@NotBlank @Size(max=64) String requestId,
                           @NotBlank @Pattern(regexp="CONFIRMED|REJECTED") String status,
                           @NotBlank @Size(max=128) String erpNo,
                           @NotBlank @Size(max=500) String message) {}
}
