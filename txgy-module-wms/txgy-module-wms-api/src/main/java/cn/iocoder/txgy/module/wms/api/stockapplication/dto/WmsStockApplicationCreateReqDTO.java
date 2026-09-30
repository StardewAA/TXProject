package cn.iocoder.txgy.module.wms.api.stockapplication.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class WmsStockApplicationCreateReqDTO {
    @NotBlank @Size(max = 64) private String requestId;
    @NotBlank @Pattern(regexp = "ERP|WMS") private String sourceSystem;
    @NotBlank @Size(max = 64) private String sourceNo;
    @NotBlank @Size(max = 64) private String businessChainNo;
    @NotBlank @Pattern(regexp = "OTHER_IN|OTHER_OUT|TRANSFER|CHECK") private String type;
    @Size(max = 500) private String remark;
    @Valid @NotEmpty @Size(max = 200) private List<Item> items;

    @Data
    public static class Item {
        @NotBlank @Size(max = 64) private String materialCode;
        @NotBlank @Size(max = 64) private String skuCode;
        @NotBlank @Size(max = 64) private String warehouseCode;
        @Size(max = 64) private String targetWarehouseCode;
        @Digits(integer = 15, fraction = 6) private BigDecimal quantity;
        @Digits(integer = 15, fraction = 6) private BigDecimal countQuantity;
        @Size(max = 500) private String remark;
    }
}
