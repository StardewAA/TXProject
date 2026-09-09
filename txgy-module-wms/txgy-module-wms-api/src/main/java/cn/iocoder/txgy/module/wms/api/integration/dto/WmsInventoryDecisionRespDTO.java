package cn.iocoder.txgy.module.wms.api.integration.dto;

import lombok.Data;

import java.util.List;

@Data
public class WmsInventoryDecisionRespDTO {

    private Boolean accepted;
    private String requestId;
    private String decisionNo;
    private String stockDecision;
    private String reservationNo;
    private Long processedEventVersion;
    private List<WmsIntegrationDocumentItemDTO> items;

}
