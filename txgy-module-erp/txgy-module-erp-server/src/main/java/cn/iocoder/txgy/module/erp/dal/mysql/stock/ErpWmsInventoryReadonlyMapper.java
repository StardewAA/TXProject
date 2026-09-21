package cn.iocoder.txgy.module.erp.dal.mysql.stock;

import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWmsInventorySnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * WMS 库存 **只读** 查询 Mapper
 *
 * <p>为什么直接读表而不是调接口：</p>
 * <ol>
 *     <li>ERP 与 WMS 共用同一个数据库（jdbc:mysql://mysql:3306/ruoyi-vue-pro）</li>
 *     <li>WMS 的 API 模块（txgy-module-wms-api）只提供主数据 upsert、库存决策、预留释放、
 *         收发货等写接口，<b>没有库存查询接口</b></li>
 *     <li>商城模块已有同类先例（ProductWmsReadonlyDatabaseAdapter 直接读 wms_inventory）</li>
 * </ol>
 *
 * <p>本类<b>只读</b>，绝不写入 WMS 表。若后续 WMS 提供库存查询接口，
 * 只需替换本 Mapper 的实现即可，上层同步服务无需改动。</p>
 *
 * <p><b>映射链</b>（WMS 建物料时按 materialCode + "-DEFAULT" 生成默认 SKU）：</p>
 * <pre>
 *   erp_product.material_code
 *     → wms_item.code
 *       → wms_item_sku.code = material_code + '-DEFAULT'
 *         → wms_inventory.sku_id
 *   仓库：erp_warehouse.wms_warehouse_code → wms_warehouse.code
 * </pre>
 *
 * @author OPENLAB BS
 */
@Mapper
public interface ErpWmsInventoryReadonlyMapper {

    /**
     * 查询已完整映射的「产品 × 仓库」实物库存快照。
     *
     * <p>采用 INNER JOIN：只返回 ERP 产品与 WMS 物料、ERP 仓库与 WMS 仓库都映射成功的组合；
     * 未映射的组合不会出现在结果中，由 {@link #selectUnmappedWarehouses} 等诊断查询单独暴露。</p>
     *
     * @param tenantId 租户编号
     * @return 实物库存快照
     */
    @Select("""
            SELECT p.id                      AS productId,
                   ew.id                     AS warehouseId,
                   sku.id                    AS wmsSkuId,
                   ww.id                     AS wmsWarehouseId,
                   COALESCE(inv.quantity, 0) AS quantity
              FROM erp_product p
              JOIN erp_warehouse ew
                ON ew.tenant_id = p.tenant_id
               AND ew.deleted = b'0'
               AND ew.wms_warehouse_code IS NOT NULL
               AND ew.wms_warehouse_code <> ''
              JOIN wms_warehouse ww
                ON ww.code = ew.wms_warehouse_code
               AND ww.tenant_id = p.tenant_id
               AND ww.deleted = b'0'
              JOIN wms_item wi
                ON wi.code = p.material_code
               AND wi.tenant_id = p.tenant_id
               AND wi.deleted = b'0'
              JOIN wms_item_sku sku
                ON sku.item_id = wi.id
               AND sku.code = CONCAT(p.material_code, '-DEFAULT')
               AND sku.deleted = b'0'
              LEFT JOIN wms_inventory inv
                ON inv.sku_id = sku.id
               AND inv.warehouse_id = ww.id
               AND inv.deleted = b'0'
             WHERE p.tenant_id = #{tenantId}
               AND p.deleted = b'0'
            """)
    List<ErpWmsInventorySnapshotDO> selectInventorySnapshot(@Param("tenantId") Long tenantId);

    /**
     * 诊断：已配置 WMS 编码、但在 WMS 侧找不到对应仓库的 ERP 仓库。
     *
     * <p>这是「仓库映射悬空」问题（ERP 的 WH01–WH05 在 WMS 不存在）的检测入口。</p>
     */
    @Select("""
            SELECT ew.wms_warehouse_code
              FROM erp_warehouse ew
             WHERE ew.tenant_id = #{tenantId}
               AND ew.deleted = b'0'
               AND ew.wms_warehouse_code IS NOT NULL
               AND ew.wms_warehouse_code <> ''
               AND NOT EXISTS (SELECT 1 FROM wms_warehouse ww
                                WHERE ww.code = ew.wms_warehouse_code
                                  AND ww.tenant_id = ew.tenant_id
                                  AND ww.deleted = b'0')
            """)
    List<String> selectUnmappedWarehouseCodes(@Param("tenantId") Long tenantId);

    /**
     * 诊断：尚未推送到 WMS 的 ERP 产品编码（WMS 侧无同编码物料）。
     */
    @Select("""
            SELECT p.material_code
              FROM erp_product p
             WHERE p.tenant_id = #{tenantId}
               AND p.deleted = b'0'
               AND NOT EXISTS (SELECT 1 FROM wms_item wi
                                WHERE wi.code = p.material_code
                                  AND wi.tenant_id = p.tenant_id
                                  AND wi.deleted = b'0')
            """)
    List<String> selectUnmappedProductCodes(@Param("tenantId") Long tenantId);

}
