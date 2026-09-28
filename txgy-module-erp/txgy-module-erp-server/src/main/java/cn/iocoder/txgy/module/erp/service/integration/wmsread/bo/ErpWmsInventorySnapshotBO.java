package cn.iocoder.txgy.module.erp.service.integration.wmsread.bo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WMS 实物库存快照（只读接口的返回模型）
 *
 * <p>与 WMS 只读接口
 * {@code GET /rpc-api/wms/inventory-read/snapshot} 的返回字段一一对应：</p>
 * <ul>
 *     <li>{@code materialCode} —— WMS 物料编码，对应 ERP {@code erp_product.material_code}</li>
 *     <li>{@code warehouseCode} —— WMS 仓库编码，对应 ERP {@code erp_warehouse.wms_warehouse_code}</li>
 *     <li>{@code skuCode} —— 真实 SKU 编码，ERP 只取默认 SKU（{@code materialCode + "-DEFAULT"}）</li>
 *     <li>{@code quantity} —— 实物库存数量，无库存记录时为 0（不是 null）</li>
 *     <li>{@code updatedAt} —— 库存记录更新时间，无库存记录时为空</li>
 * </ul>
 *
 * @author OPENLAB BS
 */
@Data
public class ErpWmsInventorySnapshotBO {

    /**
     * WMS 物料编码
     */
    private String materialCode;
    /**
     * WMS 仓库编码
     */
    private String warehouseCode;
    /**
     * WMS SKU 主键
     */
    private Long skuId;
    /**
     * WMS SKU 编码
     */
    private String skuCode;
    /**
     * WMS 仓库主键
     */
    private Long warehouseId;
    /**
     * 实物库存数量
     */
    private BigDecimal quantity;
    /**
     * 库存记录更新时间
     */
    private LocalDateTime updatedAt;

}
