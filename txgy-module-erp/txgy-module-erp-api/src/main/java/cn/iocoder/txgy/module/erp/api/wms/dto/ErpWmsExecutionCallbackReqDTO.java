package cn.iocoder.txgy.module.erp.api.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ErpWmsExecutionCallbackReqDTO extends ErpWmsCallbackBaseReqDTO {

    @NotBlank(message = "ERP 来源单号不能为空")
    private String sourceOrderNo;

    @NotBlank(message = "WMS 单号不能为空")
    private String wmsOrderNo;

    @NotBlank(message = "orderType 不能为空")
    private String orderType;

    @NotBlank(message = "executionStatus 不能为空")
    private String executionStatus;

    @Valid
    private List<ErpWmsDocumentItemDTO> items;

    private String reason;

}
