package cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class WmsReceiptSimulationReqVO {
    @NotBlank @Pattern(regexp="PURCHASE_RECEIPT|PRODUCTION_RECEIPT|SALE_SHIPMENT|PRODUCTION_SHIPMENT")
    private String orderType = "PURCHASE_RECEIPT";
    @NotNull private Long warehouseId;
    @NotBlank @Size(max=40) private String sourceOrderNo;
    @NotEmpty @Size(max=100) @Valid private List<Line> details;
    @Data public static class Line {
        @NotNull private Long skuId;
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=16,fraction=2)
        private BigDecimal quantity;
        @DecimalMin("0") @Digits(integer=14,fraction=2)
        private BigDecimal price;
    }
}
