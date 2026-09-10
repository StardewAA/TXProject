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

    /** 协同单类型：PURCHASE_COLLABORATION/SALE_COLLABORATION/AWARD_COLLABORATION/SHORTAGE_COLLABORATION，缺省视为缺料采购协同单 */
    private String collaborationType;
    private String sourceModule;
    private String sourceOrderNo;
    private String sourceEnterpriseCode;
    private String targetEnterpriseCode;
    private String currency;

    /** 来源生产工单号：SHORTAGE_COLLABORATION 必填，其余类型可选 */
    private String sourceProductionOrderNo;

    private String sourceCollaborationGNo;
    private String sourceSaleOrderNo;

    private LocalDateTime requiredAt;

    @Valid
    @NotEmpty(message = "协作单 B 明细不能为空")
    private List<ErpWmsDocumentItemDTO> items;

}
