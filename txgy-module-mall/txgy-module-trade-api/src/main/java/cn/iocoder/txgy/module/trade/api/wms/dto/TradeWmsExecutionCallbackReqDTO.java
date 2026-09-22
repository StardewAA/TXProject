package cn.iocoder.txgy.module.trade.api.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TradeWmsExecutionCallbackReqDTO {

    @NotBlank
    @Size(max = 64)
    private String requestId;

    @NotNull
    private Long tenantId;

    @NotNull
    @Positive
    private Long eventVersion;

    @NotNull
    private LocalDateTime occurredAt;

    @NotBlank
    @Size(max = 64)
    private String businessChainNo;

    @NotBlank
    @Size(max = 56)
    private String sourceOrderNo;

    @NotBlank
    @Size(max = 64)
    private String wmsOrderNo;

    @NotBlank
    @Pattern(regexp = "SALE_SHIPMENT")
    private String orderType;

    @NotBlank
    @Pattern(regexp = "COMPLETED")
    private String executionStatus;

    @Valid
    @NotEmpty
    private List<TradeWmsExecutionCallbackItemDTO> items;
}
