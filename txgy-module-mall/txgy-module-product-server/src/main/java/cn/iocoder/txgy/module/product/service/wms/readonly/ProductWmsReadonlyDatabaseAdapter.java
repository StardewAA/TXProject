package cn.iocoder.txgy.module.product.service.wms.readonly;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 本地 WMS 只读查询模拟实现：直接读取同库 WMS 表。
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.wms.readonly", name = "mode", havingValue = "mock", matchIfMissing = true)
public class ProductWmsReadonlyDatabaseAdapter implements ProductWmsReadonlyQueryAdapter {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<ProductWmsSkuSummaryDTO> getSkuSummary(Long itemId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Integer itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wms_item WHERE id = ? AND tenant_id = ? AND deleted = 0",
                Integer.class, itemId, tenantId);
        if (itemCount == null || itemCount == 0) {
            throw exception(ErrorCodeConstants.WMS_ITEM_NOT_EXISTS);
        }

        String sql = """
                SELECT item.id            AS item_id,
                       item.code          AS item_code,
                       item.name          AS item_name,
                       sku.id             AS sku_id,
                       sku.code           AS sku_code,
                       sku.name           AS sku_name,
                       item.unit          AS unit,
                       sku.selling_price   AS selling_price,
                       COALESCE(inv.total_quantity, 0) AS total_quantity
                FROM wms_item item
                         INNER JOIN wms_item_sku sku
                                    ON sku.item_id = item.id
                                        AND sku.deleted = 0
                                        AND sku.tenant_id = item.tenant_id
                         LEFT JOIN (
                    SELECT sku_id, tenant_id, SUM(quantity) AS total_quantity
                    FROM wms_inventory
                    WHERE deleted = 0
                      AND tenant_id = ?
                    GROUP BY sku_id, tenant_id
                ) inv ON inv.sku_id = sku.id AND inv.tenant_id = item.tenant_id
                WHERE item.id = ?
                  AND item.tenant_id = ?
                  AND item.deleted = 0
                ORDER BY sku.id
                """;
        return jdbcTemplate.query(sql, ps -> {
            ps.setLong(1, tenantId);
            ps.setLong(2, itemId);
            ps.setLong(3, tenantId);
        }, (rs, rowNum) -> {
            ProductWmsSkuSummaryDTO summary = new ProductWmsSkuSummaryDTO();
            summary.setItemId(rs.getLong("item_id"));
            summary.setItemCode(rs.getString("item_code"));
            summary.setItemName(rs.getString("item_name"));
            summary.setSkuId(rs.getLong("sku_id"));
            summary.setSkuCode(rs.getString("sku_code"));
            summary.setSkuName(rs.getString("sku_name"));
            summary.setUnit(rs.getString("unit"));
            summary.setSellingPrice(rs.getBigDecimal("selling_price"));
            BigDecimal totalQuantity = rs.getBigDecimal("total_quantity");
            summary.setTotalQuantity(totalQuantity != null ? totalQuantity : BigDecimal.ZERO);
            return summary;
        });
    }

}
