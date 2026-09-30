package cn.iocoder.txgy.module.erp.dal.dataobject.stock;

import lombok.Data;

import java.math.BigDecimal;

/**
 * WMS 库存快照（只读查询投影）
 *
 * <p>由 {@link cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpWmsInventoryReadonlyMapper}
 * 按「ERP 产品 → WMS 物料 → 默认 SKU → WMS 库存」映射链查出，用于填充
 * {@link ErpStockMirrorDO}（ERP 实物库存镜像）。</p>
 *
 * @author OPENLAB BS
 */
@Data
public class ErpWmsInventorySnapshotDO {

    /**
     * ERP 产品编号
     */
    private Long productId;
    /**
     * ERP 仓库编号
     */
    private Long warehouseId;
    /**
     * WMS SKU 编号
     */
    private Long wmsSkuId;
    /**
     * WMS 仓库编号
     */
    private Long wmsWarehouseId;
    /**
     * WMS 实物数量
     */
    private BigDecimal quantity;

}
