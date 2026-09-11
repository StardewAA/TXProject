package cn.iocoder.txgy.module.erp.api.wms.dto;

import lombok.Data;

@Data
public class ErpCollaborationBReceiveRespDTO {

    private Boolean accepted;
    private String requestId;
    private String collaborationNo;
    private String technicalStatus;
    private String businessStatus;
    private Long processedEventVersion;

}
