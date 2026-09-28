package cn.iocoder.txgy.module.wms.api.integration.dto;

import lombok.Data;

@Data
public class WmsIntegrationAcceptedRespDTO {

    private Boolean accepted;
    private String requestId;
    private String targetOrderNo;
    private String businessStatus;
    private Long processedVersion;

}
