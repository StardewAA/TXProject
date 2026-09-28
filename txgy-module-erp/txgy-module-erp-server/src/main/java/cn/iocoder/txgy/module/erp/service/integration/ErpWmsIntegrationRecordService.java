package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsIntegrationRecordDO;
import cn.iocoder.txgy.module.erp.dal.mysql.integration.ErpWmsIntegrationRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ErpWmsIntegrationRecordService {

    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @Resource
    private ErpWmsIntegrationRecordMapper recordMapper;

    public void createOutbound(String requestId, String aggregateType, String aggregateNo,
                               Long eventVersion, Object request) {
        createOutbound(requestId, "ERP_TO_WMS", aggregateType, aggregateNo, eventVersion, request);
    }

    /**
     * 通用出站记录：direction 由调用方指定（ERP_TO_WMS / ERP_TO_MES）。
     */
    public void createOutbound(String requestId, String direction, String aggregateType, String aggregateNo,
                               Long eventVersion, Object request) {
        if (recordMapper.selectByRequestId(requestId) != null) {
            return;
        }
        recordMapper.insert(ErpWmsIntegrationRecordDO.builder()
                .requestId(requestId).direction(direction)
                .aggregateType(aggregateType).aggregateNo(aggregateNo)
                .eventVersion(eventVersion).processingStatus(PENDING)
                .requestPayload(JsonUtils.toJsonString(request)).retryCount(0).build());
    }

    public boolean hasProcessedInbound(String requestId) {
        ErpWmsIntegrationRecordDO record = recordMapper.selectByRequestId(requestId);
        return record != null && SUCCESS.equals(record.getProcessingStatus());
    }

    public void createInboundSuccess(String requestId, String aggregateType, String aggregateNo,
                                     Long eventVersion, Object request) {
        if (recordMapper.selectByRequestId(requestId) != null) {
            return;
        }
        recordMapper.insert(ErpWmsIntegrationRecordDO.builder()
                .requestId(requestId).direction("WMS_TO_ERP")
                .aggregateType(aggregateType).aggregateNo(aggregateNo)
                .eventVersion(eventVersion).processingStatus(SUCCESS)
                .requestPayload(JsonUtils.toJsonString(request)).responsePayload("true").retryCount(0).build());
    }

    public void markSuccess(String requestId, Object response) {
        ErpWmsIntegrationRecordDO record = recordMapper.selectByRequestId(requestId);
        if (record == null) {
            return;
        }
        recordMapper.updateById(new ErpWmsIntegrationRecordDO().setId(record.getId())
                .setProcessingStatus(SUCCESS).setResponsePayload(JsonUtils.toJsonString(response))
                .setErrorMessage(null).setNextRetryTime(null));
    }

    public void markFailed(String requestId, Throwable error) {
        ErpWmsIntegrationRecordDO record = recordMapper.selectByRequestId(requestId);
        if (record == null) {
            return;
        }
        int retryCount = record.getRetryCount() == null ? 1 : record.getRetryCount() + 1;
        recordMapper.updateById(new ErpWmsIntegrationRecordDO().setId(record.getId())
                .setProcessingStatus(FAILED).setRetryCount(retryCount)
                .setErrorMessage(error.getMessage()).setNextRetryTime(LocalDateTime.now().plusMinutes(5)));
    }

}
