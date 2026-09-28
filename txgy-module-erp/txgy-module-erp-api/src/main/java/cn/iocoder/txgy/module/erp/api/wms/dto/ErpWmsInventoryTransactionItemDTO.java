package cn.iocoder.txgy.module.erp.api.wms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * WMS 库存事务明细 DTO（方案A）
 *
 * @author OPENLAB BS
 */
@Data
public class ErpWmsInventoryTransactionItemDTO {

    @NotNull(message = "lineNo 不能为空")
    private Integer lineNo;

    /**
     * 物料编码，对应 ERP 的 {@code erp_product.material_code}
     */
    @NotNull(message = "materialCode 不能为空")
    private String materialCode;

    /**
     * SKU 编码，WMS 侧为 {@code materialCode + "-DEFAULT"}，ERP 不据此建单，仅做留痕
     */
    private String skuCode;

    /**
     * 仓库编码，对应 ERP 的 {@code erp_warehouse.wms_warehouse_code}；为空时取单据头的 warehouseCode
     */
    private String warehouseCode;

    /**
     * 数量：入库/出库填正数；盘点调整填带符号数（正=盘盈，负=盘亏）
     */
    @NotNull(message = "quantity 不能为空")
    private BigDecimal quantity;

    private String batchNo;

    private String remark;

}
