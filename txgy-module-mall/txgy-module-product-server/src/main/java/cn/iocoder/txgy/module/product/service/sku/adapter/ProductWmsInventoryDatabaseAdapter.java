package cn.iocoder.txgy.module.product.service.sku.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** 本地 WMS 模拟实现：以 product_sku.stock 为物理库存，以预占表计算可售库存。 */
@Component
public class ProductWmsInventoryDatabaseAdapter implements ProductWmsInventoryAdapter {

    private static final int RESERVED = 0;
    private static final int RELEASED = 1;
    private static final int OUTBOUNDED = 2;

    private final JdbcTemplate jdbcTemplate;

    public ProductWmsInventoryDatabaseAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int getAvailableStock(Long wmsSkuId, Long warehouseId) {
        InventoryRow row = lockInventoryRow(wmsSkuId, warehouseId, false);
        return row == null ? 0 : row.available();
    }

    @Override
    public InventorySnapshot getSnapshot(Long wmsSkuId, Long warehouseId) {
        InventoryRow row = lockInventoryRow(wmsSkuId, warehouseId, false);
        if (row == null) {
            return new InventorySnapshot(false, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
        return new InventorySnapshot(true, row.physical(), row.locked(), row.available());
    }

    @Override
    public Map<String, ReservationSummary> getReservationSummaries(Collection<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(orderNos.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT order_no, status, SUM(quantity) quantity FROM wms_inventory_reservation " +
                        "WHERE order_no IN (" + placeholders + ") AND deleted=0 GROUP BY order_no, status",
                orderNos.toArray());
        Map<String, int[]> counts = new HashMap<>();
        rows.forEach(row -> {
            int[] c = counts.computeIfAbsent((String) row.get("order_no"), k -> new int[4]);
            int status = ((Number) row.get("status")).intValue();
            int qty = ((BigDecimal) row.get("quantity")).intValue();
            c[0] += qty;
            if (status == RESERVED) c[1] += qty;
            if (status == RELEASED) c[2] += qty;
            if (status == OUTBOUNDED) c[3] += qty;
        });
        return counts.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
            int[] c = e.getValue();
            String status = c[1] > 0 ? (c[2] > 0 || c[3] > 0 ? "MIXED" : "LOCKED")
                    : (c[3] > 0 ? "OUTBOUNDED" : "RELEASED");
            return new ReservationSummary(status, c[0], c[1], c[2], c[3]);
        }));
    }

    @Override
    public void reserve(String orderNo, List<Item> items) {
        changeReservations(orderNo, items, RESERVED);
    }

    @Override
    public void release(String orderNo, List<Item> items) {
        changeReservations(orderNo, items, RELEASED);
    }

    @Override
    public void outbound(String orderNo, List<Item> items) {
        for (Item item : items) {
            int updated = jdbcTemplate.update("UPDATE wms_inventory_reservation SET status=? " +
                            "WHERE order_no=? AND sku_id=? AND warehouse_id=? AND status=? AND deleted=0",
                    OUTBOUNDED, orderNo, item.wmsSkuId(), item.warehouseId(), RESERVED);
            if (updated > 0) {
                jdbcTemplate.update("UPDATE wms_inventory SET quantity=quantity-? WHERE sku_id=? AND warehouse_id=? AND quantity>=? AND deleted=0",
                        item.count(), item.wmsSkuId(), item.warehouseId(), item.count());
            }
        }
    }

    @Override
    public void inboundReturn(String returnNo, String orderNo, List<Item> items) {
        for (Item item : items) {
            int inserted = jdbcTemplate.update("INSERT IGNORE INTO wms_inventory_return " +
                            "(return_no, order_no, sku_id, warehouse_id, quantity) VALUES (?,?,?,?,?)",
                    returnNo, orderNo, item.wmsSkuId(), item.warehouseId(), item.count());
            if (inserted > 0) {
                jdbcTemplate.update("UPDATE wms_inventory SET quantity=quantity+? WHERE sku_id=? AND warehouse_id=? AND deleted=0",
                        item.count(), item.wmsSkuId(), item.warehouseId());
            }
        }
    }

    @Transactional
    private void changeReservations(String orderNo, List<Item> items, int targetStatus) {
        for (Item item : items) {
            InventoryRow row = lockInventoryRow(item.wmsSkuId(), item.warehouseId(), true);
            if (row == null || (targetStatus == RESERVED && row.available() < item.count())) {
                throw new IllegalStateException("本地 WMS 可售库存不足: " + item.wmsSkuId());
            }
            if (targetStatus == RESERVED) {
                jdbcTemplate.update("INSERT INTO wms_inventory_reservation (order_no, sku_id, warehouse_id, quantity, status) " +
                                "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status), quantity=VALUES(quantity)",
                        orderNo, item.wmsSkuId(), item.warehouseId(), item.count(), RESERVED);
            } else {
                jdbcTemplate.update("UPDATE wms_inventory_reservation SET status=? WHERE order_no=? AND sku_id=? " +
                                "AND warehouse_id=? AND status=? AND deleted=0",
                        targetStatus, orderNo, item.wmsSkuId(), item.warehouseId(), RESERVED);
            }
        }
    }

    private InventoryRow lockInventoryRow(Long wmsSkuId, Long warehouseId, boolean forUpdate) {
        List<BigDecimal> stocks = jdbcTemplate.query("SELECT quantity FROM wms_inventory WHERE sku_id=? AND warehouse_id=? AND deleted=0 " +
                        (forUpdate ? "FOR UPDATE" : ""),
                ps -> { ps.setLong(1, wmsSkuId); ps.setLong(2, warehouseId); },
                (rs, i) -> rs.getBigDecimal(1));
        if (stocks.isEmpty()) return null;
        BigDecimal locked = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM wms_inventory_reservation " +
                        "WHERE sku_id=? AND warehouse_id=? AND status=? AND deleted=0",
                BigDecimal.class, wmsSkuId, warehouseId, RESERVED);
        return new InventoryRow(stocks.get(0), locked == null ? BigDecimal.ZERO : locked);
    }

    private record InventoryRow(BigDecimal physical, BigDecimal locked) {
        int available() { return Math.max(physical.subtract(locked).intValue(), 0); }
    }

}

