package cn.iocoder.txgy.module.erp.api.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ErpCollaborationBReceiveReqDTO extends ErpWmsCallbackBaseReqDTO {

    @NotBlank(message = "协作单 B 编号不能为空")
    private String collaborationNo;

    @NotBlank(message = "来源生产工单号不能为空")
    private String sourceProductionOrderNo;

    private String sourceCollaborationGNo;
    private String sourceSaleOrderNo;

    private LocalDateTime requiredAt;

    @Valid
    @NotEmpty(message = "协作单 B 明细不能为空")
    private List<ErpWmsDocumentItemDTO> items;

}
