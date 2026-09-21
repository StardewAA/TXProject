package cn.iocoder.txgy.module.wms.service.integration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationBaseReqDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationRequestRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.integration.WmsIntegrationRequestDO;
import cn.iocoder.txgy.module.wms.dal.mysql.integration.WmsIntegrationRequestMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

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

    /**
     * 来源业务幂等与传输 requestId 分离。复用台账唯一键串行化同一来源的并发请求，
     * 单据、明细和两层台账一起提交/回滚。重试时间不属于业务内容。
     */
    @Transactional(rollbackFor = Exception.class)
    public WmsIntegrationRequestRespDTO executePurchaseReceiptBusiness(WmsOrderRequestDTO request,
                                                                       Supplier<WmsIntegrationRequestRespDTO> action) {
        return executeOrderBusiness(request, action);
    }

    @Transactional(rollbackFor = Exception.class)
    public WmsIntegrationRequestRespDTO executeOrderBusiness(WmsOrderRequestDTO request,
                                                            Supplier<WmsIntegrationRequestRespDTO> action) {
        WmsOrderRequestDTO business = JsonUtils.parseObject(JsonUtils.toJsonString(request), WmsOrderRequestDTO.class);
        // 业务幂等键带上事件版本：同一来源单在缺料恢复后重新申请（eventVersion 递增）时，
        // 应作为新的业务请求受理，而不是与上一版内容比对后报"业务内容或业务链不一致"。
        String identity = JsonUtils.toJsonString(java.util.List.of(request.getTenantId(), request.getSourceModule(),
                request.getOrderType(), request.getSourceOrderNo(), request.getEventVersion()));
        business.setRequestId("B:" + DigestUtil.sha256Hex(identity).substring(0, 62));
        business.setOccurredAt(null);
        business.setItems(business.getItems().stream()
                .sorted(java.util.Comparator.comparing(cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO::getLineNo))
                .peek(item -> item.setRequiredQuantity(item.getRequiredQuantity().stripTrailingZeros())).toList());
        try {
            return execute(request.getOrderType() + "_BUSINESS", business, action);
        } catch (cn.iocoder.txgy.framework.common.exception.ServiceException ex) {
            if (java.util.Objects.equals(ex.getCode(), INTEGRATION_REQUEST_DUPLICATE_CONFLICT.getCode())) {
                throw exception(cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_SOURCE_ORDER_CONFLICT);
            }
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public WmsIntegrationRequestRespDTO execute(String operationType, WmsIntegrationBaseReqDTO request,
                                                 Supplier<WmsIntegrationRequestRespDTO> action) {
        String requestJson = JsonUtils.toJsonString(request);
        String digest = DigestUtil.sha256Hex(requestJson);
        WmsIntegrationRequestDO existing = requestMapper.selectByRequestId(request.getRequestId());
        if (existing != null) return replay(existing, operationType, digest);

        WmsIntegrationRequestDO processing = new WmsIntegrationRequestDO()
                .setRequestId(request.getRequestId())
                .setOperationType(operationType)
                .setPayloadDigest(digest)
                .setProcessingStatus("PROCESSING");
        try {
            requestMapper.insert(processing);
        } catch (DataIntegrityViolationException ex) {
            WmsIntegrationRequestDO concurrent = requestMapper.selectByRequestIdCurrent(request.getRequestId());
            if (concurrent == null) throw ex;
            return replay(concurrent, operationType, digest);
        }

        WmsIntegrationRequestRespDTO response = action.get();
        response.setRequestId(request.getRequestId());
        response.setProcessingStatus(SUCCESS);
        response.setUpdatedAt(LocalDateTime.now());
        requestMapper.updateById(processing
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
        if (!SUCCESS.equals(existing.getProcessingStatus()) || existing.getResponsePayload() == null) {
            throw exception(INTEGRATION_REQUEST_DUPLICATE_CONFLICT);
        }
        return JsonUtils.parseObject(existing.getResponsePayload(), WmsIntegrationRequestRespDTO.class);
    }
}
