package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.wms.api.integration.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.txgy.module.wms.enums.integration.WmsIntegrationConstants.*;

/**
 * WMS 尚未完成正式接口时使用的契约一致模拟实现。
 * 模拟实现只返回仓储结果，不修改 ERP 库存。
 */
@Service
@ConditionalOnProperty(prefix = "txgy.integration.wms", name = "mode", havingValue = "mock", matchIfMissing = true)
public class ErpMockWmsIntegrationGateway implements ErpWmsIntegrationGateway {

    @Value("${txgy.integration.wms.mock.stock-decision:PENDING}")
    private String mockStockDecision;

    private final Map<String, WmsIntegrationRequestRespDTO> requestResults = new ConcurrentHashMap<>();
    private final Map<String, WmsInventoryDecisionRespDTO> saleOrderDecisions = new ConcurrentHashMap<>();
    private final Map<String, WmsIntegrationAcceptedRespDTO> acceptedResults = new ConcurrentHashMap<>();
    private final Map<String, WmsIntegrationRequestRespDTO> orderResults = new ConcurrentHashMap<>();

    @Override
    public WmsIntegrationAcceptedRespDTO upsertMaterial(WmsMaterialUpsertReqDTO reqDTO) {
        return accept(reqDTO.getRequestId(), reqDTO.getMaterialCode(), "SYNCED", reqDTO.getDataVersion());
    }

    @Override
    public WmsIntegrationAcceptedRespDTO upsertPartner(WmsPartnerUpsertReqDTO reqDTO) {
        return accept(reqDTO.getRequestId(), reqDTO.getPartnerCode(), "SYNCED", reqDTO.getDataVersion());
    }

    @Override
    public WmsIntegrationAcceptedRespDTO upsertWarehouse(WmsWarehouseUpsertReqDTO reqDTO) {
        String code = reqDTO.getWarehouseCode() != null ? reqDTO.getWarehouseCode()
                : "WH-MOCK-" + stableSuffix(reqDTO.getRequestId());
        return accept(reqDTO.getRequestId(), code, "SYNCED", reqDTO.getDataVersion());
    }

    @Override
    public WmsInventoryDecisionRespDTO createInventoryDecision(WmsInventoryDecisionReqDTO reqDTO) {
        WmsIntegrationRequestRespDTO existing = requestResults.get(reqDTO.getRequestId());
        if (existing != null) {
            return saleOrderDecisions.get(reqDTO.getSourceOrderNo());
        }
        String decision = normalizeDecision(mockStockDecision);
        List<WmsIntegrationDocumentItemDTO> items = reqDTO.getItems().stream()
                .map(item -> buildDecisionItem(item, decision)).toList();
        WmsInventoryDecisionRespDTO response = new WmsInventoryDecisionRespDTO()
                .setAccepted(true)
                .setRequestId(reqDTO.getRequestId())
                .setDecisionNo("PD-MOCK-" + stableSuffix(reqDTO.getRequestId()))
                .setStockDecision(decision)
                .setReservationNo(DECISION_PENDING.equals(decision) || DECISION_SHORTAGE.equals(decision) ? null
                        : "RS-MOCK-" + stableSuffix(reqDTO.getRequestId()))
                .setProcessedEventVersion(reqDTO.getEventVersion())
                .setItems(items);
        saleOrderDecisions.put(reqDTO.getSourceOrderNo(), response);
        saveRequestResult(reqDTO.getRequestId(), "INVENTORY_DECISION", response.getDecisionNo(), decision,
                reqDTO.getEventVersion());
        return response;
    }

    @Override
    public WmsIntegrationAcceptedRespDTO releaseReservation(WmsReservationReleaseReqDTO reqDTO) {
        WmsIntegrationAcceptedRespDTO response = accept(reqDTO.getRequestId(), reqDTO.getReservationNo(),
                RESERVATION_RELEASED, reqDTO.getEventVersion());
        saveRequestResult(reqDTO.getRequestId(), "RESERVATION", reqDTO.getReservationNo(),
                RESERVATION_RELEASED, reqDTO.getEventVersion());
        return response;
    }

    @Override
    public WmsIntegrationAcceptedRespDTO createPurchaseReceipt(WmsOrderRequestDTO reqDTO) {
        return acceptOrder(reqDTO, "RK-MOCK-");
    }

    @Override
    public WmsIntegrationAcceptedRespDTO createCustomerReturnReceipt(WmsOrderRequestDTO reqDTO) {
        return acceptOrder(reqDTO, "THRK-MOCK-");
    }

    @Override
    public WmsIntegrationAcceptedRespDTO createPurchaseReturnShipment(WmsOrderRequestDTO reqDTO) {
        return acceptOrder(reqDTO, "THCK-MOCK-");
    }

    @Override
    public WmsIntegrationRequestRespDTO getRequestResult(String requestId) {
        return requestResults.get(requestId);
    }

    @Override
    public WmsInventoryDecisionRespDTO getInventoryDecision(Long tenantId, String saleOrderNo) {
        return saleOrderDecisions.get(saleOrderNo);
    }

    @Override
    public WmsIntegrationRequestRespDTO getOrderExecution(Long tenantId, String sourceOrderNo, String orderType) {
        return orderResults.get(orderType + ":" + sourceOrderNo);
    }

    private WmsIntegrationAcceptedRespDTO acceptOrder(WmsOrderRequestDTO reqDTO, String prefix) {
        String orderNo = prefix + stableSuffix(reqDTO.getRequestId());
        WmsIntegrationAcceptedRespDTO response = accept(reqDTO.getRequestId(), orderNo, "RECEIVED",
                reqDTO.getEventVersion());
        saveRequestResult(reqDTO.getRequestId(), reqDTO.getOrderType(), orderNo, "RECEIVED", reqDTO.getEventVersion());
        orderResults.put(reqDTO.getOrderType() + ":" + reqDTO.getSourceOrderNo(),
                requestResults.get(reqDTO.getRequestId()));
        return response;
    }

    private WmsIntegrationAcceptedRespDTO accept(String requestId, String targetNo, String status, Long version) {
        return acceptedResults.computeIfAbsent(requestId, key -> new WmsIntegrationAcceptedRespDTO()
                .setAccepted(true)
                .setRequestId(key)
                .setTargetOrderNo(targetNo)
                .setBusinessStatus(status)
                .setProcessedVersion(version));
    }

    private void saveRequestResult(String requestId, String aggregateType, String aggregateNo, String status,
                                   Long version) {
        requestResults.computeIfAbsent(requestId, key -> new WmsIntegrationRequestRespDTO()
                .setRequestId(key)
                .setAggregateType(aggregateType)
                .setAggregateNo(aggregateNo)
                .setProcessingStatus(status)
                .setProcessedEventVersion(version)
                .setResultCode(0)
                .setUpdatedAt(LocalDateTime.now()));
    }

    private WmsIntegrationDocumentItemDTO buildDecisionItem(WmsIntegrationDocumentItemDTO source, String decision) {
        BigDecimal required = source.getRequiredQuantity();
        BigDecimal reserved = BigDecimal.ZERO;
        if (DECISION_FULL.equals(decision)) {
            reserved = required;
        } else if (DECISION_PARTIAL.equals(decision)) {
            reserved = required.divide(BigDecimal.valueOf(2), 6, RoundingMode.DOWN);
        }
        return new WmsIntegrationDocumentItemDTO()
                .setLineNo(source.getLineNo())
                .setSourceLineNo(source.getSourceLineNo())
                .setMaterialCode(source.getMaterialCode())
                .setSkuCode(source.getSkuCode())
                .setMaterialType(source.getMaterialType())
                .setMaterialNameSnapshot(source.getMaterialNameSnapshot())
                .setSpecificationSnapshot(source.getSpecificationSnapshot())
                .setUnitCode(source.getUnitCode())
                .setUnitNameSnapshot(source.getUnitNameSnapshot())
                .setWarehouseCode(source.getWarehouseCode())
                .setRequiredQuantity(required)
                .setReservedQuantity(reserved)
                .setShortageQuantity(required.subtract(reserved));
    }

    private String normalizeDecision(String decision) {
        if (DECISION_FULL.equals(decision) || DECISION_PARTIAL.equals(decision)
                || DECISION_SHORTAGE.equals(decision) || DECISION_REJECTED.equals(decision)) {
            return decision;
        }
        return DECISION_PENDING;
    }

    private String stableSuffix(String requestId) {
        return Integer.toUnsignedString(requestId.hashCode());
    }

}
