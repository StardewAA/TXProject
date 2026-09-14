package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WmsShipmentExecutionServiceTest {
    private WmsShipmentExecutionService service;
    private cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderMapper orders;
    private cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderDetailMapper details;
    private cn.iocoder.txgy.module.erp.api.wms.ErpWmsCallbackApi erp;
    @org.junit.jupiter.api.BeforeEach void setup() {
        service = new WmsShipmentExecutionService();
        orders = org.mockito.Mockito.mock(cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderMapper.class);
        details = org.mockito.Mockito.mock(cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderDetailMapper.class);
        erp = org.mockito.Mockito.mock(cn.iocoder.txgy.module.erp.api.wms.ErpWmsCallbackApi.class);
        // Only the database and remote HTTP boundary are mocked.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "orderMapper", orders);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "detailMapper", details);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "erp", erp);
        cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.setTenantId(2005L);
    }
    @org.junit.jupiter.api.AfterEach void cleanup() { cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.clear(); }
    @Test void executedQuantityUsesConfirmedShipmentQuantity() {
        org.mockito.Mockito.when(orders.selectById(1L)).thenReturn(new WmsShipmentOrderDO().setId(1L).setExecutionStatus(1));
        org.mockito.Mockito.when(details.selectListByOrderId(1L)).thenReturn(java.util.List.of(
            new cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDetailDO().setQuantity(new java.math.BigDecimal("7"))));
        assertEquals(new java.math.BigDecimal("7"), service.getExecution(1L).get("completedQuantity"));
    }
    @Test void simulationNeverCallsRealErp() {
        org.mockito.Mockito.when(orders.selectByIdForUpdate(1L)).thenReturn(new WmsShipmentOrderDO().setSimulation(true).setSourceModule("ERP").setExecutionStatus(1));
        assertFalse(service.retryNotification(1L));
        org.mockito.Mockito.verifyNoInteractions(erp);
    }
    @Test void failedNotificationCanRetryWithSameIdAndSnapshot() {
        var order = new WmsShipmentOrderDO().setId(1L).setNo("WMS-1").setBizOrderNo("PO-1").setBusinessChainNo("BC-1")
            .setSourceModule("ERP").setExecutionStatus(1).setExecutionTime(java.time.LocalDateTime.of(2026,9,12,10,0));
        org.mockito.Mockito.when(orders.selectByIdForUpdate(1L)).thenReturn(order);
        org.mockito.Mockito.when(details.selectListByOrderId(1L)).thenReturn(java.util.List.of(
            new cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDetailDO()
              .setSourceSnapshotJson("{\"lineNo\":1,\"materialCode\":\"MAT-SNAPSHOT\",\"unitCode\":\"KG\"}")
              .setSourceLineNo(1).setQuantity(new java.math.BigDecimal("7"))));
        org.mockito.Mockito.when(erp.callbackExecutionResult(org.mockito.ArgumentMatchers.any()))
            .thenReturn(cn.iocoder.txgy.framework.common.pojo.CommonResult.success(false))
            .thenReturn(cn.iocoder.txgy.framework.common.pojo.CommonResult.success(true));
        assertFalse(service.retryNotification(1L));
        assertEquals("FAILED", order.getNotificationStatus());
        assertNotNull(order.getNextNotificationTime());
        assertTrue(service.retryNotification(1L));
        assertEquals("DELIVERED", order.getNotificationStatus());
        var captor = org.mockito.ArgumentCaptor.forClass(cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsExecutionCallbackReqDTO.class);
        org.mockito.Mockito.verify(erp, org.mockito.Mockito.times(2)).callbackExecutionResult(captor.capture());
        var requests = captor.getAllValues();
        assertEquals(requests.get(0).getRequestId(), requests.get(1).getRequestId());
        assertEquals("COMPLETED", requests.get(1).getExecutionStatus());
        assertEquals("SALE_SHIPMENT", requests.get(1).getOrderType());
        assertEquals("PO-1", requests.get(1).getSourceOrderNo());
        assertEquals("MAT-SNAPSHOT", requests.get(1).getItems().getFirst().getMaterialCode());
        assertEquals(new java.math.BigDecimal("7"), requests.get(1).getItems().getFirst().getActualQuantity());
        assertEquals(2, order.getNotificationAttempts());
    }
    @Test void automaticRetryWaitsButManualRetryCanProceed() {
        var order = new WmsShipmentOrderDO().setId(1L).setSourceModule("ERP").setExecutionStatus(1)
                .setNotificationStatus("FAILED").setNextNotificationTime(java.time.LocalDateTime.now().plusMinutes(10));
        org.mockito.Mockito.when(orders.selectByIdForUpdate(1L)).thenReturn(order);
        assertFalse(service.notifyIfDue(1L));
        org.mockito.Mockito.verifyNoInteractions(erp, details);
    }
    @Test void deliveredOrderDoesNotSendAgain() {
        org.mockito.Mockito.when(orders.selectByIdForUpdate(1L)).thenReturn(new WmsShipmentOrderDO()
                .setSourceModule("ERP").setExecutionStatus(1).setNotificationStatus("DELIVERED"));
        assertTrue(service.retryNotification(1L));
        org.mockito.Mockito.verifyNoInteractions(erp, details);
    }
    @Test void draftQuantityRemainsZeroAndRejectionIsVisible() {
        org.mockito.Mockito.when(orders.selectById(1L)).thenReturn(new WmsShipmentOrderDO()
                .setExecutionStatus(0).setApprovalStatus(3));
        var result = service.getExecution(1L);
        assertEquals("REJECTED", result.get("status"));
        assertEquals(java.math.BigDecimal.ZERO, result.get("completedQuantity"));
        org.mockito.Mockito.verifyNoInteractions(details);
    }
    @Test void manualOrderNeedsNoErpNotification() {
        assertEquals("NOT_REQUIRED", WmsShipmentExecutionService.notificationStatus(new WmsShipmentOrderDO()));
    }
    @Test void executedErpOrderWithoutDeliveryMarkerRemainsPending() {
        assertEquals("PENDING", WmsShipmentExecutionService.notificationStatus(
                new WmsShipmentOrderDO().setSourceModule("ERP").setExecutionStatus(1)));
    }
    @Test void draftIsNotReportedAsDelivered() {
        assertEquals("WAITING_EXECUTION", WmsShipmentExecutionService.notificationStatus(
                new WmsShipmentOrderDO().setSourceModule("ERP").setExecutionStatus(0)));
    }
}



