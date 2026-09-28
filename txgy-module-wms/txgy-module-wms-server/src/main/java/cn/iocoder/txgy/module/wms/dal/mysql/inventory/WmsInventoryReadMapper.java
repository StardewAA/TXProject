package cn.iocoder.txgy.module.wms.dal.mysql.inventory;

import cn.iocoder.txgy.module.wms.api.inventoryread.dto.WmsInventorySnapshotRespDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.Collection;
import java.util.List;

@Mapper
public interface WmsInventoryReadMapper {
    @Select("""
        <script>
        SELECT i.code material_code, w.code warehouse_code, s.id sku_id, s.code sku_code,
               w.id warehouse_id, COALESCE(v.quantity, 0) quantity, v.update_time updated_at
        FROM wms_item i
        JOIN wms_item_sku s ON s.item_id=i.id AND s.tenant_id=i.tenant_id AND s.deleted=0
        CROSS JOIN wms_warehouse w
        LEFT JOIN wms_inventory v ON v.sku_id=s.id AND v.warehouse_id=w.id
          AND v.tenant_id=i.tenant_id AND v.deleted=0
        WHERE i.tenant_id=#{tenantId} AND i.deleted=0 AND w.tenant_id=#{tenantId} AND w.deleted=0
        <if test="materialCodes != null and !materialCodes.isEmpty()">
          AND i.code IN <foreach item="code" collection="materialCodes" open="(" separator="," close=")">#{code}</foreach>
        </if>
        <if test="warehouseCodes != null and !warehouseCodes.isEmpty()">
          AND w.code IN <foreach item="code" collection="warehouseCodes" open="(" separator="," close=")">#{code}</foreach>
        </if>
        ORDER BY i.code, w.code, s.id
        </script>
        """)
    List<WmsInventorySnapshotRespDTO> selectSnapshot(@Param("tenantId") Long tenantId,
            @Param("materialCodes") Collection<String> materialCodes,
            @Param("warehouseCodes") Collection<String> warehouseCodes);

    @Select("""
        <script>SELECT code FROM wms_warehouse WHERE tenant_id=#{tenantId} AND deleted=0
        <if test="codes != null and !codes.isEmpty()">AND code IN <foreach item="code" collection="codes" open="(" separator="," close=")">#{code}</foreach></if>
        ORDER BY code</script>
        """)
    List<String> selectExistingWarehouseCodes(@Param("tenantId") Long tenantId, @Param("codes") Collection<String> codes);

    @Select("""
        <script>SELECT code FROM wms_item WHERE tenant_id=#{tenantId} AND deleted=0
        <if test="codes != null and !codes.isEmpty()">AND code IN <foreach item="code" collection="codes" open="(" separator="," close=")">#{code}</foreach></if>
        ORDER BY code</script>
        """)
    List<String> selectExistingMaterialCodes(@Param("tenantId") Long tenantId, @Param("codes") Collection<String> codes);
}
