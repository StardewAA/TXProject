package cn.iocoder.txgy.module.wms.api.stock.dto;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
@Data
@EqualsAndHashCode(callSuper = true)
public class WmsStockHistoryPageReqDTO extends PageParam {
    private String materialCode;
    private String materialName;
    private String skuCode;
    private String warehouseCode;
    private String orderNo;
    private Integer orderType;
    private String sourceOrderNo;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
