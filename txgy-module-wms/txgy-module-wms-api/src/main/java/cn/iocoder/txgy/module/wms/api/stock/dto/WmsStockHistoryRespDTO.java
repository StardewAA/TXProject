package cn.iocoder.txgy.module.wms.api.stock.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class WmsStockHistoryRespDTO {
    private Long id;
    private Long warehouseId;
    private Long skuId;
    private String materialCode;
    private String materialName;
    private String skuCode;
    private String skuName;
    private String unitName;
    private String warehouseCode;
    private String warehouseName;
    private BigDecimal beforeQuantity;
    private BigDecimal quantity;
    private BigDecimal afterQuantity;
    private Long orderId;
    private String orderNo;
    private Integer orderType;
    private String sourceOrderNo;
    private String businessChainNo;
    private LocalDateTime createTime;
    private String remark;
}
