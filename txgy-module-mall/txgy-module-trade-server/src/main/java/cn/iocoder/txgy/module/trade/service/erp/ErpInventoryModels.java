package cn.iocoder.txgy.module.trade.service.erp;

import java.time.LocalDateTime;
import java.util.List;

public final class ErpInventoryModels {
    private ErpInventoryModels() {}
    public record Item(Long skuId, int count, Long wmsSkuId, Long wmsWarehouseId) {}
    public record Entry(String id, Long orderId, String orderNo, String operation, String businessKey,
                        String mode, String status, String message, int attempts, String erpNo,
                        Long logisticsId, String logisticsNo, List<Item> items, LocalDateTime updateTime) {
        public boolean confirmed() { return "CONFIRMED".equals(status); }
    }
}
