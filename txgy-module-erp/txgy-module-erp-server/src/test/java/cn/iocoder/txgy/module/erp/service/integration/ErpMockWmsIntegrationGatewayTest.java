package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsInventoryDecisionReqDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsInventoryDecisionRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsReservationReleaseReqDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.txgy.module.wms.enums.integration.WmsIntegrationConstants.DECISION_FULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ErpMockWmsIntegrationGatewayTest {

    @Test
    void shouldReturnFullReservationAndKeepRequestIdempotent() {
        ErpMockWmsIntegrationGateway gateway = new ErpMockWmsIntegrationGateway();
        ReflectionTestUtils.setField(gateway, "mockStockDecision", DECISION_FULL);
        WmsInventoryDecisionReqDTO request = new WmsInventoryDecisionReqDTO()
                .setBusinessChainNo("BC-TEST-1").setSourceOrderNo("SO-TEST-1")
                .setCustomerCode("CUST-TEST").setEventVersion(1L)
                .setItems(List.of(new WmsIntegrationDocumentItemDTO().setLineNo(1)
                        .setMaterialCode("MAT-TEST").setSkuCode("MAT-TEST-DEFAULT")
                        .setMaterialType("FINISHED_GOODS").setMaterialNameSnapshot("测试成品")
                        .setUnitCode("PCS").setUnitNameSnapshot("件")
                        .setRequiredQuantity(new BigDecimal("12.500000"))));
        request.setRequestId("REQUEST-TEST-1");
        request.setTenantId(1L);

        WmsInventoryDecisionRespDTO first = gateway.createInventoryDecision(request);
        WmsInventoryDecisionRespDTO second = gateway.createInventoryDecision(request);

        assertSame(first, second);
        assertEquals(DECISION_FULL, first.getStockDecision());
        assertEquals(new BigDecimal("12.500000"), first.getItems().getFirst().getReservedQuantity());
        assertEquals(BigDecimal.ZERO.setScale(6), first.getItems().getFirst().getShortageQuantity());
        assertEquals(1L, gateway.getRequestResult("REQUEST-TEST-1").getProcessedEventVersion());
    }

    @Test
    void shouldReleaseReservationIdempotently() {
        ErpMockWmsIntegrationGateway gateway = new ErpMockWmsIntegrationGateway();
        WmsReservationReleaseReqDTO request = new WmsReservationReleaseReqDTO()
                .setBusinessChainNo("BC-TEST-2").setSaleOrderNo("SO-TEST-2")
                .setReservationNo("RS-TEST-2").setReasonCode("CANCELLED")
                .setReason("测试取消").setEventVersion(2L);
        request.setRequestId("REQUEST-TEST-2");
        request.setTenantId(1L);

        var first = gateway.releaseReservation(request);
        var second = gateway.releaseReservation(request);

        assertSame(first, second);
        assertEquals("RELEASED", first.getBusinessStatus());
        assertEquals(2L, gateway.getRequestResult("REQUEST-TEST-2").getProcessedEventVersion());
    }

    @Test
    void shouldQueryBusinessOrderBySourceNumber() {
        ErpMockWmsIntegrationGateway gateway = new ErpMockWmsIntegrationGateway();
        WmsOrderRequestDTO request = new WmsOrderRequestDTO().setBusinessChainNo("BC-PI-1")
                .setSourceOrderNo("PI-TEST-1").setOrderType("PURCHASE_RECEIPT")
                .setEventVersion(1L).setItems(List.of());
        request.setRequestId("REQUEST-TEST-3");
        request.setTenantId(1L);

        var response = gateway.createPurchaseReceipt(request);
        var query = gateway.getOrderExecution(1L, "PI-TEST-1", "PURCHASE_RECEIPT");

        assertEquals(response.getTargetOrderNo(), query.getAggregateNo());
        assertEquals("RECEIVED", query.getProcessingStatus());
    }
}
