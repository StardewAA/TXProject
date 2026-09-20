package cn.iocoder.txgy.module.product.service.sku.adapter;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 已停用的旧 WMS 库存履约契约，仅保留兼容错误提示。
 *
 * <p>库存变更统一由商城 ERP 适配层处理；WMS 只提供只读摘要，不能据此推算可承诺库存。</p>
 */
public interface ProductWmsInventoryAdapter {

    int getAvailableStock(Long wmsSkuId, Long warehouseId);

    InventorySnapshot getSnapshot(Long wmsSkuId, Long warehouseId);

    Map<String, ReservationSummary> getReservationSummaries(Collection<String> orderNos);

    void reserve(String orderNo, List<Item> items);

    void release(String orderNo, List<Item> items);

    void outbound(String orderNo, List<Item> items);

    void inboundReturn(String returnNo, String orderNo, List<Item> items);

    record Item(Long wmsSkuId, Long warehouseId, Integer count) {
    }

    record InventorySnapshot(boolean exists, BigDecimal physicalQuantity, BigDecimal lockedQuantity,
                             int availableStock) {
    }

    record ReservationSummary(String status, int totalCount, int lockedCount, int releasedCount,
                              int outboundCount) {
    }
}

