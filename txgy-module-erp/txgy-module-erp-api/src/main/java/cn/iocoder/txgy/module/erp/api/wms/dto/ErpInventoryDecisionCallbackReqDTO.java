package cn.iocoder.txgy.module.erp.api.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ErpInventoryDecisionCallbackReqDTO extends ErpWmsCallbackBaseReqDTO {

    @NotBlank(message = "销售订单号不能为空")
    private String saleOrderNo;

    @NotBlank(message = "decisionNo 不能为空")
    private String decisionNo;

    @NotBlank(message = "stockDecision 不能为空")
    private String stockDecision;

    private String reservationNo;

    @Valid
    @NotEmpty(message = "库存判断结果明细不能为空")
    private List<ErpWmsDocumentItemDTO> items;

    private String reason;

}
