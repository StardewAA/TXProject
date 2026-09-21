package cn.iocoder.txgy.module.wms.api.integration;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.api.integration.dto.*;
import cn.iocoder.txgy.module.wms.service.integration.WmsIntegrationRequestService;
import cn.iocoder.txgy.module.wms.service.integration.WmsMaterialIntegrationService;
import cn.iocoder.txgy.module.wms.service.integration.WmsPartnerIntegrationService;
import cn.iocoder.txgy.module.wms.service.integration.WmsWarehouseIntegrationService;
import cn.iocoder.txgy.module.wms.service.order.receipt.WmsReceiptOrderService;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemSkuMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.merchant.WmsMerchantMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_FEATURE_NOT_ENABLED;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_TENANT_MISMATCH;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_RECEIPT_REQUEST_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WmsIntegrationApiImplTest {

    @InjectMocks private WmsIntegrationApiImpl api;
    @Mock private WmsIntegrationRequestService requestService;
    @Mock private WmsMaterialIntegrationService materialService;
    @Mock private WmsPartnerIntegrationService partnerService;
    @Mock private WmsWarehouseIntegrationService warehouseService;
    @Mock private WmsReceiptOrderService receiptOrderService;
    @Mock private WmsItemSkuMapper itemSkuMapper;
    @Mock private WmsItemMapper itemMapper;
    @Mock private WmsMerchantMapper merchantMapper;
    @Mock private WmsWarehouseMapper warehouseMapper;
    @Mock private cn.iocoder.txgy.module.wms.service.order.shipment.WmsShipmentOrderService shipmentOrderService;
    @Mock private cn.iocoder.txgy.module.wms.dal.mysql.order.receipt.WmsReceiptOrderDetailMapper receiptDetailMapper;
    @Mock private cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderDetailMapper shipmentDetailMapper;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(2005L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void enabledMasterDataEndpointsRouteThroughLedger() {
        doAnswer(invocation -> ((Supplier<WmsIntegrationRequestRespDTO>) invocation.getArgument(2)).get())
                .when(requestService).execute(anyString(), any(), any());
        WmsMaterialUpsertReqDTO material = base(new WmsMaterialUpsertReqDTO(), "material-1");
        WmsPartnerUpsertReqDTO partner = base(new WmsPartnerUpsertReqDTO(), "partner-1");
        WmsWarehouseUpsertReqDTO warehouse = base(new WmsWarehouseUpsertReqDTO(), "warehouse-1");
        when(materialService.upsert(material)).thenReturn(accepted("material-1", "MAT-001", 1L));
        when(partnerService.upsert(partner)).thenReturn(accepted("partner-1", "PARTNER-001", 2L));
        when(warehouseService.upsert(warehouse)).thenReturn(accepted("warehouse-1", "WMS-WH-001", 3L));

        assertEquals("MAT-001", api.upsertMaterial(material).getData().getTargetOrderNo());
        assertEquals("PARTNER-001", api.upsertPartner(partner).getData().getTargetOrderNo());
        assertEquals("WMS-WH-001", api.upsertWarehouse(warehouse).getData().getTargetOrderNo());
        verify(requestService).execute(eq("MATERIAL_UPSERT"), same(material), any());
        verify(requestService).execute(eq("PARTNER_UPSERT"), same(partner), any());
        verify(requestService).execute(eq("WAREHOUSE_UPSERT"), same(warehouse), any());
    }

    @Test
    void rejectsMismatchedTenantBeforeBusinessExecution() {
        WmsMaterialUpsertReqDTO material = base(new WmsMaterialUpsertReqDTO(), "material-1");
        material.setTenantId(9999L);
        assertServiceException(() -> api.upsertMaterial(material), INTEGRATION_TENANT_MISMATCH);
        verifyNoInteractions(materialService, requestService);
    }

    @Test
    void resultLookupUsesCurrentTenantContext() {
        WmsIntegrationRequestRespDTO response = new WmsIntegrationRequestRespDTO().setRequestId("request-1");
        when(requestService.getResult("request-1")).thenReturn(response);
        assertEquals(response, api.getRequestResult("request-1").getData());
        verify(requestService).getResult("request-1");
    }

    @Test
    void purchaseExecutionLookupReturnsCurrentReceiptStatus() {
        var order = new cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO()
                .setId(1L).setNo("WMS-ERP-LOOKUP").setBizOrderNo("LOOKUP").setSourceModule("ERP")
                .setExecutionStatus(1).setApprovalStatus(2).setTotalQuantity(new java.math.BigDecimal("10"));
        when(receiptOrderService.getReceiptOrderPage(any())).thenReturn(
                new cn.iocoder.txgy.framework.common.pojo.PageResult<>(java.util.List.of(order), 1L));
        var result = api.getOrderExecution(2005L, "LOOKUP", "PURCHASE_RECEIPT").getCheckedData();
        assertEquals("WMS-ERP-LOOKUP", result.getAggregateNo());
        assertEquals("SUCCESS", result.getProcessingStatus());
        assertEquals(1, result.getResultData().get("executionStatus"));
    }

    @Test
    void deferredEndpointsReturnDedicatedError() {
        assertServiceException(() -> api.createInventoryDecision(null), INTEGRATION_RECEIPT_REQUEST_INVALID,
                "请求不能为空");
        assertServiceException(() -> api.releaseReservation(null), INTEGRATION_RECEIPT_REQUEST_INVALID,
                "请求不能为空");
        WmsOrderRequestDTO request = base(new WmsOrderRequestDTO(), "purchase-receipt-1")
                .setBusinessChainNo("BC-1").setSourceOrderNo("RK-ERP-1").setOrderType("PURCHASE_RECEIPT")
                .setEventVersion(1L).setItems(java.util.List.of(new WmsIntegrationDocumentItemDTO()
                        .setLineNo(1).setMaterialCode("MAT-1").setSkuCode("MAT-1-DEFAULT")
                        .setMaterialType("RAW_MATERIAL").setMaterialNameSnapshot("钢管")
                        .setUnitCode("PC").setUnitNameSnapshot("件").setWarehouseCode("WH-1")
                        .setRequiredQuantity(new java.math.BigDecimal("10"))));
        doAnswer(invocation -> ((Supplier<WmsIntegrationRequestRespDTO>) invocation.getArgument(2)).get())
                .when(requestService).execute(anyString(), same(request), any());
        doAnswer(invocation -> ((Supplier<WmsIntegrationRequestRespDTO>) invocation.getArgument(1)).get())
                .when(requestService).executePurchaseReceiptBusiness(same(request), any());
        when(warehouseMapper.selectByCode("WH-1")).thenReturn(new WmsWarehouseDO().setId(77L).setWarehouseType("RAW_MATERIAL"));
        when(itemSkuMapper.selectByCode("MAT-1-DEFAULT")).thenReturn(new WmsItemSkuDO().setId(66L).setItemId(55L));
        when(itemMapper.selectById(55L)).thenReturn(new WmsItemDO().setId(55L).setCode("MAT-1").setMaterialType("RAW_MATERIAL"));
        when(receiptOrderService.createReceiptOrder(any())).thenReturn(88L);
        assertEquals("WMS-ERP-RK-ERP-1", api.createPurchaseReceipt(request).getData().getTargetOrderNo());
        verify(receiptOrderService).createReceiptOrder(argThat(vo ->
                vo.getWarehouseId().equals(77L) && vo.getDetails().getFirst().getSkuId().equals(66L)
                        && vo.getBizOrderNo().equals("RK-ERP-1") && vo.getDetails().size() == 1
                        && vo.getDetails().getFirst().getQuantity().compareTo(new java.math.BigDecimal("10")) == 0));
        assertServiceException(() -> api.createCustomerReturnReceipt(null), INTEGRATION_FEATURE_NOT_ENABLED);
        assertServiceException(() -> api.createPurchaseReturnShipment(null), INTEGRATION_FEATURE_NOT_ENABLED);
        assertServiceException(() -> api.getInventoryDecision(2005L, "SO-1"), INTEGRATION_FEATURE_NOT_ENABLED);
        assertServiceException(() -> api.getOrderExecution(2005L, "PO-1", "PURCHASE"),
                INTEGRATION_FEATURE_NOT_ENABLED);
    }

    @Test
    void rejectsMallOrderNumberThatWouldOverflowGeneratedWmsNumber() {
        WmsOrderRequestDTO request = base(new WmsOrderRequestDTO(), "mall-request-1")
                .setShopId(12L)
                .setBusinessChainNo("MALL-CHAIN-1").setSourceOrderNo("M".repeat(56))
                .setOrderType("SALE_SHIPMENT").setEventVersion(1L)
                .setItems(java.util.List.of(new WmsIntegrationDocumentItemDTO().setLineNo(1)
                        .setMaterialCode("FG-1").setSkuCode("FG-1-STD").setMaterialType("FINISHED_GOODS")
                        .setMaterialNameSnapshot("成品").setUnitCode("PC").setUnitNameSnapshot("件")
                        .setWarehouseCode("FG-WH").setRequiredQuantity(java.math.BigDecimal.ONE)));
        request.setSourceModule("MALL");

        assertServiceException(() -> api.createMallShipment(request), INTEGRATION_RECEIPT_REQUEST_INVALID,
                "来源单号不能为空且不能超过 55 字符");
        verifyNoInteractions(requestService);
    }

    @Test
    void rejectsMallShipmentWithoutPositiveShopBeforeLedgerOrOrderWrite() {
        WmsOrderRequestDTO request = mallShipmentRequest();
        request.setShopId(null);

        assertServiceException(() -> api.createMallShipment(request), INTEGRATION_RECEIPT_REQUEST_INVALID,
                "商城出库申请必须提供有效店铺编号");

        verifyNoInteractions(requestService, shipmentOrderService);
    }

    @Test
    void mallShipmentPersistsAndVerifiesShopAndSingleWarehouseOwnership() {
        WmsOrderRequestDTO request = mallShipmentRequest();
        doAnswer(invocation -> ((Supplier<WmsIntegrationRequestRespDTO>) invocation.getArgument(2)).get())
                .when(requestService).execute(anyString(), same(request), any());
        doAnswer(invocation -> ((Supplier<WmsIntegrationRequestRespDTO>) invocation.getArgument(1)).get())
                .when(requestService).executeOrderBusiness(same(request), any());
        when(warehouseMapper.selectByCode("FG-WH"))
                .thenReturn(new WmsWarehouseDO().setId(77L).setWarehouseType("FINISHED_GOODS"));
        when(itemSkuMapper.selectByCode("FG-1-STD"))
                .thenReturn(new WmsItemSkuDO().setId(66L).setItemId(55L));
        when(itemMapper.selectById(55L)).thenReturn(new WmsItemDO().setId(55L).setCode("FG-1")
                .setMaterialType("FINISHED_GOODS").setUnit("件"));
        when(shipmentOrderService.createShipmentOrder(any())).thenReturn(88L);
        when(shipmentOrderService.getShipmentOrder(88L)).thenReturn(
                new cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDO()
                        .setId(88L).setShopId(12L).setWarehouseId(77L));

        assertEquals("WMS-MALL-MALL-ORDER-1", api.createMallShipment(request).getData().getTargetOrderNo());

        verify(shipmentOrderService).createShipmentOrder(argThat(vo -> Objects.equals(vo.getShopId(), 12L)
                && Objects.equals(vo.getWarehouseId(), 77L) && vo.getDetails().size() == 1));
        verify(shipmentOrderService).getShipmentOrder(88L);
    }

    private static WmsOrderRequestDTO mallShipmentRequest() {
        WmsOrderRequestDTO request = base(new WmsOrderRequestDTO(), "mall-request-1")
                .setShopId(12L).setBusinessChainNo("MALL-CHAIN-1").setSourceOrderNo("MALL-ORDER-1")
                .setOrderType("SALE_SHIPMENT").setEventVersion(1L)
                .setItems(java.util.List.of(new WmsIntegrationDocumentItemDTO().setLineNo(1)
                        .setMaterialCode("FG-1").setSkuCode("FG-1-STD").setMaterialType("FINISHED_GOODS")
                        .setMaterialNameSnapshot("成品").setUnitCode("PC").setUnitNameSnapshot("件")
                        .setWarehouseCode("FG-WH").setRequiredQuantity(java.math.BigDecimal.ONE)));
        request.setSourceModule("MALL");
        return request;
    }

    private static <T extends WmsIntegrationBaseReqDTO> T base(T request, String requestId) {
        request.setRequestId(requestId);
        request.setTenantId(2005L);
        request.setSourceModule("ERP");
        request.setOccurredAt(LocalDateTime.of(2026, 9, 8, 10, 0));
        return request;
    }

    private static WmsIntegrationAcceptedRespDTO accepted(String requestId, String targetNo, Long version) {
        return new WmsIntegrationAcceptedRespDTO().setAccepted(true).setRequestId(requestId)
                .setTargetOrderNo(targetNo).setBusinessStatus("SYNCED").setProcessedVersion(version);
    }
}
