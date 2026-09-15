package cn.iocoder.txgy.module.wms.api.stockapplication.dto;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data
public class WmsStockApplicationActionReqDTO {
    @NotNull private Long id;
    private Long userId;
}
