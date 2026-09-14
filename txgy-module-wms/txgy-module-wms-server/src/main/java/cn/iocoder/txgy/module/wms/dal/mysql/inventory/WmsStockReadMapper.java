package cn.iocoder.txgy.module.wms.dal.mysql.inventory;
import cn.iocoder.txgy.module.wms.api.stock.dto.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Explicit tenant predicates cover every joined table, including RPC reads. */
@Mapper
public interface WmsStockReadMapper {
    @Select("""
        <script>
        SELECT h.*, i.code material_code, i.name material_name, i.unit unit_name,
               s.code sku_code, s.name sku_name, w.code warehouse_code, w.name warehouse_name,
               COALESCE(r.biz_order_no,c.biz_order_no) source_order_no,
               COALESCE(r.business_chain_no,c.business_chain_no) business_chain_no
        FROM wms_inventory_history h
        LEFT JOIN wms_item_sku s ON s.id=h.sku_id AND s.tenant_id=h.tenant_id AND s.deleted=0
        LEFT JOIN wms_item i ON i.id=s.item_id AND i.tenant_id=h.tenant_id AND i.deleted=0
        LEFT JOIN wms_warehouse w ON w.id=h.warehouse_id AND w.tenant_id=h.tenant_id AND w.deleted=0
        LEFT JOIN wms_receipt_order r ON h.order_type=1 AND r.id=h.order_id AND r.tenant_id=h.tenant_id AND r.deleted=0
        LEFT JOIN wms_shipment_order c ON h.order_type=2 AND c.id=h.order_id AND c.tenant_id=h.tenant_id AND c.deleted=0
        WHERE h.deleted=0 AND h.tenant_id=#{tenantId}
        <if test="q.materialCode != null and q.materialCode != ''">AND i.code=#{q.materialCode}</if>
        <if test="q.materialName != null and q.materialName != ''">AND i.name LIKE CONCAT('%',#{q.materialName},'%')</if>
        <if test="q.skuCode != null and q.skuCode != ''">AND s.code=#{q.skuCode}</if>
        <if test="q.warehouseCode != null and q.warehouseCode != ''">AND w.code=#{q.warehouseCode}</if>
        <if test="q.orderNo != null and q.orderNo != ''">AND h.order_no=#{q.orderNo}</if>
        <if test="q.orderType != null">AND h.order_type=#{q.orderType}</if>
        <if test="q.sourceOrderNo != null and q.sourceOrderNo != ''">AND COALESCE(r.biz_order_no,c.biz_order_no)=#{q.sourceOrderNo}</if>
        <if test="q.startTime != null">AND h.create_time &gt;= #{q.startTime}</if>
        <if test="q.endTime != null">AND h.create_time &lt;= #{q.endTime}</if>
        ORDER BY h.create_time DESC,h.id DESC
        </script>
        """)
    IPage<WmsStockHistoryRespDTO> selectHistoryPage(Page<WmsStockHistoryRespDTO> page,
            @Param("tenantId") Long tenantId, @Param("q") WmsStockHistoryPageReqDTO request);

    @Select("""
        <script>
        SELECT v.id, i.code AS material_code, i.name AS material_name,
               s.code AS sku_code, s.name AS sku_name, i.unit AS unit_name,
               w.code AS warehouse_code, w.name AS warehouse_name, v.quantity
        FROM wms_inventory v
        JOIN wms_item_sku s ON s.id=v.sku_id AND s.tenant_id=v.tenant_id AND s.deleted=0
        JOIN wms_item i ON i.id=s.item_id AND i.tenant_id=v.tenant_id AND i.deleted=0
        JOIN wms_warehouse w ON w.id=v.warehouse_id AND w.tenant_id=v.tenant_id AND w.deleted=0
        WHERE v.deleted=0 AND v.tenant_id=#{tenantId}
        <if test="q.materialCode != null and q.materialCode != ''">AND i.code=#{q.materialCode}</if>
        <if test="q.materialName != null and q.materialName != ''">AND i.name LIKE CONCAT('%',#{q.materialName},'%')</if>
        <if test="q.skuCode != null and q.skuCode != ''">AND s.code=#{q.skuCode}</if>
        <if test="q.warehouseCode != null and q.warehouseCode != ''">AND w.code=#{q.warehouseCode}</if>
        ORDER BY w.id,i.id,s.id,v.id
        </script>
        """)
    IPage<WmsStockRespDTO> selectStockPage(Page<WmsStockRespDTO> page,
            @Param("tenantId") Long tenantId, @Param("q") WmsStockPageReqDTO request);

    @Select("""
        SELECT code,name,warehouse_type,status,address,principal,remark,sort FROM wms_warehouse
        WHERE tenant_id=#{tenantId} AND deleted=0 ORDER BY sort,id
        """)
    List<WmsWarehouseReadRespDTO> selectWarehouses(@Param("tenantId") Long tenantId);

    @Select("""
        SELECT i.code AS material_code,i.name AS material_name,
               s.code AS sku_code,s.name AS sku_name,i.unit AS unit_name
        FROM wms_item_sku s
        JOIN wms_item i ON i.id=s.item_id AND i.tenant_id=s.tenant_id AND i.deleted=0
        WHERE s.tenant_id=#{tenantId} AND s.deleted=0 AND i.status=0
        ORDER BY i.id,s.id
        """)
    List<cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO.Sku>
        selectSkuOptions(@Param("tenantId") Long tenantId);
}
