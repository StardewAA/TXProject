package cn.iocoder.txgy.module.wms.api.integration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class WmsReservationReleaseReqDTO extends WmsIntegrationBaseReqDTO {

    @NotBlank(message = "businessChainNo 不能为空")
    private String businessChainNo;

    @NotBlank(message = "销售订单号不能为空")
    private String saleOrderNo;

    @NotBlank(message = "reservationNo 不能为空")
    private String reservationNo;

    @NotBlank(message = "reasonCode 不能为空")
    private String reasonCode;

    @NotBlank(message = "释放原因不能为空")
    private String reason;

    @NotNull(message = "eventVersion 不能为空")
    private Long eventVersion;

    @Valid
    private List<WmsIntegrationDocumentItemDTO> items;

}
