package cn.iocoder.txgy.module.wms.api.integration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class WmsInventoryDecisionReqDTO extends WmsIntegrationBaseReqDTO {

    @NotBlank(message = "businessChainNo 不能为空")
    private String businessChainNo;

    @NotBlank(message = "销售订单号不能为空")
    private String sourceOrderNo;

    @NotBlank(message = "customerCode 不能为空")
    private String customerCode;

    private LocalDateTime requiredAt;

    @NotNull(message = "eventVersion 不能为空")
    private Long eventVersion;

    @Valid
    @NotEmpty(message = "库存判断明细不能为空")
    private List<WmsIntegrationDocumentItemDTO> items;

}
