package cn.iocoder.txgy.module.wms.api.stock.dto;
import lombok.Data;
import java.math.BigDecimal;

/** A WMS SKU/warehouse inventory row. IDs belong exclusively to WMS. */
@Data
public class WmsStockRespDTO {
    private Long id;
    private String materialCode;
    private String materialName;
    private String skuCode;
    private String skuName;
    private String unitName;
    private String warehouseCode;
    private String warehouseName;
    private BigDecimal quantity;
}

