package cn.iocoder.txgy.module.wms.api.integration.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class WmsIntegrationRequestRespDTO {

    private String requestId;
    private String aggregateType;
    private String aggregateNo;
    private String processingStatus;
    private Long processedEventVersion;
    private Integer resultCode;
    private Map<String, Object> resultData;
    private LocalDateTime updatedAt;

}
