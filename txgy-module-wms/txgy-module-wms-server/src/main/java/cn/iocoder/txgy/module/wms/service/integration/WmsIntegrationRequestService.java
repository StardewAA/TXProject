package cn.iocoder.txgy.module.wms.service.integration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationBaseReqDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationRequestRespDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.integration.WmsIntegrationRequestDO;
import cn.iocoder.txgy.module.wms.dal.mysql.integration.WmsIntegrationRequestMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.Supplier;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_REQUEST_DUPLICATE_CONFLICT;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_REQUEST_NOT_EXISTS;

@Service
public class WmsIntegrationRequestService {

    private static final String SUCCESS = "SUCCESS";

    @Resource
    private WmsIntegrationRequestMapper requestMapper;

    @Transactional(rollbackFor = Exception.class)
    public WmsIntegrationRequestRespDTO execute(String operationType, WmsIntegrationBaseReqDTO request,
                                                 Supplier<WmsIntegrationRequestRespDTO> action) {
        String requestJson = JsonUtils.toJsonString(request);
        String digest = DigestUtil.sha256Hex(requestJson);
        WmsIntegrationRequestDO existing = requestMapper.selectByRequestId(request.getRequestId());
        if (existing != null) {
            return replay(existing, operationType, digest);
        }

        WmsIntegrationRequestRespDTO response = action.get();
        response.setRequestId(request.getRequestId());
        response.setProcessingStatus(SUCCESS);
        response.setUpdatedAt(LocalDateTime.now());
        requestMapper.insert(new WmsIntegrationRequestDO()
                .setRequestId(request.getRequestId())
                .setOperationType(operationType)
                .setPayloadDigest(digest)
                .setAggregateType(response.getAggregateType())
                .setAggregateNo(response.getAggregateNo())
                .setProcessingStatus(SUCCESS)
                .setProcessedEventVersion(response.getProcessedEventVersion())
                .setResultCode(response.getResultCode())
                .setResponsePayload(JsonUtils.toJsonString(response)));
        return response;
    }

    public WmsIntegrationRequestRespDTO getResult(String requestId) {
        WmsIntegrationRequestDO record = requestMapper.selectByRequestId(requestId);
        if (record == null) {
            throw exception(INTEGRATION_REQUEST_NOT_EXISTS);
        }
        return JsonUtils.parseObject(record.getResponsePayload(), WmsIntegrationRequestRespDTO.class);
    }

    private WmsIntegrationRequestRespDTO replay(WmsIntegrationRequestDO existing, String operationType,
                                                 String payloadDigest) {
        if (!existing.getOperationType().equals(operationType) || !existing.getPayloadDigest().equals(payloadDigest)) {
            throw exception(INTEGRATION_REQUEST_DUPLICATE_CONFLICT);
        }
        return JsonUtils.parseObject(existing.getResponsePayload(), WmsIntegrationRequestRespDTO.class);
    }
}
