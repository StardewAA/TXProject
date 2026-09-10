package cn.iocoder.txgy.module.wms.api.integration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class WmsOrderRequestDTO extends WmsIntegrationBaseReqDTO {

    @NotBlank(message = "businessChainNo 不能为空")
    private String businessChainNo;

    @NotBlank(message = "sourceOrderNo 不能为空")
    private String sourceOrderNo;

    private String originalOrderNo;
    private String partnerCode;

    @NotBlank(message = "orderType 不能为空")
    private String orderType;

    @NotNull(message = "eventVersion 不能为空")
    private Long eventVersion;

    @Valid
    @NotEmpty(message = "业务单据明细不能为空")
    private List<WmsIntegrationDocumentItemDTO> items;

}
