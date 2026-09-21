package cn.iocoder.txgy.module.trade.api.wms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeWmsExecutionCallbackItemDTO {

    @NotNull
    private Integer lineNo;

    @NotNull
    private Integer sourceLineNo;

    @NotBlank
    private String materialCode;

    @NotBlank
    private String skuCode;

    @NotNull
    @DecimalMin(value = "0.000001")
    private BigDecimal actualQuantity;
}
