package cn.iocoder.txgy.module.product.service.sku.adapter;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * WMS 库存适配契约。
 *
 * <p>当前只开放 WMS 实时只读库存；预占、释放、出库和退货入库尚未开放。</p>
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

