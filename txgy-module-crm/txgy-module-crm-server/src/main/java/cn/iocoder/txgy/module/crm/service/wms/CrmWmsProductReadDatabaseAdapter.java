package cn.iocoder.txgy.module.crm.service.wms;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 本地 WMS 商品只读查询实现：直接读取同库 WMS 表，作为 WMS 远程接口不可用前的联调方式。
 *
 * <p>查询条件与 WMS 接口保持一致：仅返回启用（status=0）、未删除的商品与规格，缺少租户上下文时拒绝查询。</p>
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.wms.crm-product", name = "mode", havingValue = "mock",
        matchIfMissing = true)
public class CrmWmsProductReadDatabaseAdapter implements CrmWmsProductReadAdapter {

    /**
     * 与 WMS {@code /admin-api/wms/crm/products/page} 相同的过滤条件
     */
    private static final String FROM_CLAUSE = """
            FROM wms_item item
                     INNER JOIN wms_item_sku sku
                                ON sku.item_id = item.id
                                    AND sku.deleted = 0
                                    AND sku.tenant_id = item.tenant_id
            WHERE item.deleted = 0
              AND item.tenant_id = ?
              AND item.status = 0
            """;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public PageResult<CrmWmsProductSkuDTO> getProductPage(Integer pageNo, Integer pageSize) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) " + FROM_CLAUSE, Long.class, tenantId);
        if (total == null || total == 0) {
            return PageResult.empty(0L);
        }
        // 每个 SKU 一行，按商品编号、规格编号稳定排序
        String sql = """
                SELECT item.id   AS item_id,
                       item.code AS material_code,
                       item.name AS material_name,
                       item.unit AS unit_name,
                       sku.id    AS sku_id,
                       sku.code  AS sku_code,
                       sku.name  AS sku_name
                """ + FROM_CLAUSE + " ORDER BY item.id, sku.id LIMIT ? OFFSET ?";
        int offset = Math.max(pageNo - 1, 0) * pageSize;
        List<CrmWmsProductSkuDTO> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            CrmWmsProductSkuDTO dto = new CrmWmsProductSkuDTO();
            dto.setItemId(rs.getLong("item_id"));
            dto.setMaterialCode(rs.getString("material_code"));
            dto.setMaterialName(rs.getString("material_name"));
            dto.setUnitName(rs.getString("unit_name"));
            dto.setSkuId(rs.getLong("sku_id"));
            dto.setSkuCode(rs.getString("sku_code"));
            dto.setSkuName(rs.getString("sku_name"));
            return dto;
        }, tenantId, pageSize, offset);
        return new PageResult<>(list, total);
    }

}
