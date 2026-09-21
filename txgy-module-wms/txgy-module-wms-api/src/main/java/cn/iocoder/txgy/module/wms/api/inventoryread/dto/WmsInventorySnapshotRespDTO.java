package cn.iocoder.txgy.module.wms.api.inventoryread.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WmsInventorySnapshotRespDTO {
    private String materialCode;
    private String warehouseCode;
    private Long skuId;
    private String skuCode;
    private Long warehouseId;
    private BigDecimal quantity;
    private LocalDateTime updatedAt;
}
