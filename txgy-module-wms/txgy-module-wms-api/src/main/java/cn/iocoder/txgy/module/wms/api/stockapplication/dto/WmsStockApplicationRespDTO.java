package cn.iocoder.txgy.module.wms.api.stockapplication.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class WmsStockApplicationRespDTO {
    private Long id;
    private String no;
    private String type;
    private String sourceSystem;
    private String sourceNo;
    private String businessChainNo;
    private String approvalStatus;
    private String executionStatus;
    private String processInstanceId;
    private String lastError;
    private String remark;
    private Long version;
    private LocalDateTime createTime;
    private List<Item> items;
    @Data
    public static class Item {
        private String materialCode;
        private String materialName;
        private String skuCode;
        private String skuName;
        private String unitName;
        private String warehouseCode;
        private String warehouseName;
        private String targetWarehouseCode;
        private String targetWarehouseName;
        private BigDecimal quantity;
        private BigDecimal baseQuantity;
        private BigDecimal countQuantity;
        private BigDecimal transferredQuantity;
        private BigDecimal receivedQuantity;
        private String remark;
    }
}
