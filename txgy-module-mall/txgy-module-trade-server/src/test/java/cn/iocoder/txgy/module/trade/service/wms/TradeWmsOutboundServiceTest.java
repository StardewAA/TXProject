package cn.iocoder.txgy.module.trade.service.wms;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.property.dto.ProductPropertyValueDetailRespDTO;
import cn.iocoder.txgy.module.product.api.sku.ProductSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.wms.TradeWmsShipmentDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.wms.TradeWmsRecoveryMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.wms.TradeWmsShipmentMapper;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApi;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsWarehouseReadRespDTO;
import cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TradeWmsOutboundServiceTest {

    private ProductSkuApi productSkuApi;
    private WmsStockReadApi stockReadApi;
    private WmsIntegrationApi integrationApi;
    private TradeWmsShipmentMapper shipmentMapper;
    private TradeWmsRecoveryMapper recoveryMapper;
    private TradeOrderMapper orderMapper;
    private TradeOrderItemMapper orderItemMapper;
    private TradeWmsOutboundService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(2009L);
        productSkuApi = mock(ProductSkuApi.class);
        stockReadApi = mock(WmsStockReadApi.class);
        integrationApi = mock(WmsIntegrationApi.class);
        shipmentMapper = mock(TradeWmsShipmentMapper.class);
        recoveryMapper = mock(TradeWmsRecoveryMapper.class);
        orderMapper = mock(TradeOrderMapper.class);
        orderItemMapper = mock(TradeOrderItemMapper.class);
        service = new TradeWmsOutboundService(productSkuApi, stockReadApi, integrationApi, shipmentMapper,
                recoveryMapper, orderMapper, orderItemMapper);
        when(shipmentMapper.selectByGroup(any(), any(), any())).thenReturn(null);
        when(integrationApi.createMallShipment(any())).thenAnswer(invocation -> {
            WmsOrderRequestDTO request = invocation.getArgument(0);
            return success(new WmsIntegrationAcceptedRespDTO().setAccepted(true)
                    .setRequestId(request.getRequestId()).setTargetOrderNo("WMS-" + request.getSourceOrderNo()));
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void splitsSingleShopByWarehouseAndUsesCanonicalUnitCode() {
        ProductSkuRespDTO first = productSku(1L, 101L, 201L, "㎡");
        ProductSkuRespDTO second = productSku(2L, 102L, 202L, "桶");
        ProductSkuRespDTO third = productSku(3L, 103L, 201L, "m²");
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(1L, first, 2L, second, 3L, third));
        when(stockReadApi.getSkuOptions()).thenReturn(success(List.of(
                wmsSku(101L, "MAT-1", "SKU-1", "m²"),
                wmsSku(102L, "MAT-2", "SKU-2", "桶"),
                wmsSku(103L, "MAT-3", "SKU-3", "平方米"))));
        when(stockReadApi.getWarehouses()).thenReturn(success(List.of(
                warehouse(201L, "FG-A"), warehouse(202L, "FG-B"))));

        TradeOrderDO order = new TradeOrderDO().setId(500L).setNo("o500").setShopId(10L);
        service.createForPaidOrder(order, List.of(
                item(11L, 1L, 10L, 2, 1200),
                item(12L, 2L, 10L, 1, 3400),
                item(13L, 3L, 10L, 3, 1500)));

        ArgumentCaptor<WmsOrderRequestDTO> captor = ArgumentCaptor.forClass(WmsOrderRequestDTO.class);
        verify(integrationApi, times(2)).createMallShipment(captor.capture());
        assertEquals(2, captor.getAllValues().stream().map(WmsOrderRequestDTO::getSourceOrderNo).distinct().count());
        for (WmsOrderRequestDTO request : captor.getAllValues()) {
            assertEquals(2009L, request.getTenantId());
            assertEquals(10L, request.getShopId());
            assertEquals(1, request.getItems().stream().map(i -> i.getWarehouseCode()).distinct().count());
            assertEquals("MALL", request.getSourceModule());
            assertTrue(request.getItems().stream().allMatch(i -> i.getUnitCode() != null));
        }
        assertEquals("M2", captor.getAllValues().stream().flatMap(r -> r.getItems().stream())
                .filter(i -> "SKU-1".equals(i.getSkuCode())).findFirst().orElseThrow().getUnitCode());
        verify(shipmentMapper, times(2)).insert(any(TradeWmsShipmentDO.class));
    }

    @Test
    void missingOrMixedShopOwnershipIsManualReviewAndNeverSubmitted() {
        ProductSkuRespDTO first = productSku(1L, 101L, 201L, "件");
        ProductSkuRespDTO second = productSku(2L, 102L, 201L, "件");
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(1L, first, 2L, second));

        TradeOrderDO legacy = new TradeOrderDO().setId(500L).setNo("o500").setShopId(null);
        service.createForPaidOrder(legacy, List.of(
                item(11L, 1L, 10L, 1, 1200), item(12L, 2L, 11L, 1, 1200)));

        verifyNoInteractions(integrationApi, stockReadApi);
        ArgumentCaptor<TradeWmsShipmentDO> record = ArgumentCaptor.forClass(TradeWmsShipmentDO.class);
        verify(shipmentMapper).insert(record.capture());
        assertNull(record.getValue().getShopId());
        assertEquals(TradeWmsOutboundService.MANUAL_REVIEW, record.getValue().getStatus());
        assertTrue(record.getValue().getLastError().contains("订单缺少有效店铺"));
    }

    @Test
    void unknownUnitIsRecordedAndNeverSubmitted() {
        ProductSkuRespDTO sku = productSku(1L, 101L, 201L, "未知包装");
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(1L, sku));
        when(stockReadApi.getSkuOptions()).thenReturn(success(List.of(wmsSku(101L, "MAT-1", "SKU-1", "件"))));
        when(stockReadApi.getWarehouses()).thenReturn(success(List.of(warehouse(201L, "FG-A"))));

        service.createForPaidOrder(new TradeOrderDO().setId(500L).setNo("o500").setShopId(10L),
                List.of(item(11L, 1L, 10L, 1, 1200)));

        verifyNoInteractions(integrationApi);
        ArgumentCaptor<TradeWmsShipmentDO> captor = ArgumentCaptor.forClass(TradeWmsShipmentDO.class);
        verify(shipmentMapper).updateById(captor.capture());
        assertEquals(TradeWmsOutboundService.UNAVAILABLE, captor.getValue().getStatus());
        assertTrue(captor.getValue().getLastError().contains("单位字典"));
    }

    @Test
    void parentOrderCanShipOnlyAfterEveryWarehouseApplicationCompletes() {
        when(shipmentMapper.selectListByOrderId(500L)).thenReturn(List.of(
                new TradeWmsShipmentDO().setStatus(TradeWmsOutboundService.COMPLETED),
                new TradeWmsShipmentDO().setStatus(TradeWmsOutboundService.PENDING)));
        ServiceException pending = assertThrows(ServiceException.class, () -> service.validateCompleted(500L));
        assertTrue(pending.getMessage().contains("1 张"));

        when(shipmentMapper.selectListByOrderId(500L)).thenReturn(List.of(
                new TradeWmsShipmentDO().setStatus(TradeWmsOutboundService.COMPLETED),
                new TradeWmsShipmentDO().setStatus(TradeWmsOutboundService.COMPLETED)));
        assertDoesNotThrow(() -> service.validateCompleted(500L));
    }

    @Test
    void productRpcFailureIsRecoveredByTenantScopedPaidOrderScan() {
        TradeOrderDO order = new TradeOrderDO().setId(500L).setNo("o500").setShopId(10L)
                .setStatus(10).setPayStatus(true);
        List<TradeOrderItemDO> items = List.of(item(11L, 1L, 10L, 1, 1200));
        when(productSkuApi.getSkuMap(any())).thenThrow(new IllegalStateException("product unavailable"));

        service.createForPaidOrder(order, items);

        verifyNoInteractions(integrationApi);
        verify(shipmentMapper, never()).insert(any(TradeWmsShipmentDO.class));

        when(recoveryMapper.selectPaidOrdersWithoutShipment(2009L, 100)).thenReturn(List.of(order));
        when(orderItemMapper.selectListByOrderId(500L)).thenReturn(items);
        ProductSkuRespDTO sku = productSku(1L, 101L, 201L, "平方米");
        doReturn(Map.of(1L, sku)).when(productSkuApi).getSkuMap(any());
        when(stockReadApi.getSkuOptions()).thenReturn(success(List.of(wmsSku(101L, "MAT-1", "SKU-1", "m²"))));
        when(stockReadApi.getWarehouses()).thenReturn(success(List.of(warehouse(201L, "FG-A"))));

        assertEquals(1, service.recoverPaidOrdersWithoutShipment(100));

        verify(recoveryMapper).selectPaidOrdersWithoutShipment(2009L, 100);
        verify(shipmentMapper).insert(any(TradeWmsShipmentDO.class));
        verify(integrationApi).createMallShipment(any(WmsOrderRequestDTO.class));
    }

    @Test
    void missingProductWmsMappingIsRecoveredAfterMappingIsCompleted() {
        TradeOrderDO order = new TradeOrderDO().setId(500L).setNo("o500").setShopId(10L)
                .setStatus(10).setPayStatus(true);
        List<TradeOrderItemDO> items = List.of(item(11L, 1L, 10L, 1, 1200));
        ProductSkuRespDTO unmapped = new ProductSkuRespDTO().setId(1L).setProperties(List.of());
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(1L, unmapped));

        service.createForPaidOrder(order, items);

        verify(shipmentMapper, never()).insert(any(TradeWmsShipmentDO.class));
        verifyNoInteractions(integrationApi);

        when(recoveryMapper.selectPaidOrdersWithoutShipment(2009L, 200)).thenReturn(List.of(order));
        when(orderItemMapper.selectListByOrderId(500L)).thenReturn(items);
        ProductSkuRespDTO mapped = productSku(1L, 101L, 201L, "平方米");
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(1L, mapped));
        when(stockReadApi.getSkuOptions()).thenReturn(success(List.of(wmsSku(101L, "MAT-1", "SKU-1", "平方米"))));
        when(stockReadApi.getWarehouses()).thenReturn(success(List.of(warehouse(201L, "FG-A"))));

        assertEquals(1, service.recoverPaidOrdersWithoutShipment(999));

        verify(recoveryMapper).selectPaidOrdersWithoutShipment(2009L, 200);
        verify(integrationApi).createMallShipment(any(WmsOrderRequestDTO.class));
    }

    @Test
    void unsentFailureRecordRebuildsRequestFromCurrentMappings() {
        TradeOrderDO order = new TradeOrderDO().setId(500L).setNo("o500").setShopId(10L)
                .setStatus(10).setPayStatus(true);
        TradeWmsShipmentDO failed = new TradeWmsShipmentDO().setId(9L).setOrderId(500L).setOrderNo("o500")
                .setShopId(10L).setWarehouseId(201L).setSourceOrderNo("M500-S10-W201")
                .setRequestId("request-9").setStatus(TradeWmsOutboundService.UNAVAILABLE)
                .setEventVersion(1L).setRetryCount(1).setRequestJson(null);
        List<TradeOrderItemDO> items = List.of(item(11L, 1L, 10L, 1, 1200));
        when(shipmentMapper.selectRetryable(100)).thenReturn(List.of(failed));
        when(orderMapper.selectById(500L)).thenReturn(order);
        when(orderItemMapper.selectListByOrderId(500L)).thenReturn(items);
        ProductSkuRespDTO sku = productSku(1L, 101L, 201L, "平方米");
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(1L, sku));
        when(stockReadApi.getSkuOptions()).thenReturn(success(List.of(wmsSku(101L, "MAT-1", "SKU-1", "m²"))));
        when(stockReadApi.getWarehouses()).thenReturn(success(List.of(warehouse(201L, "FG-A"))));

        assertEquals(1, service.retryAndSync(100));

        ArgumentCaptor<WmsOrderRequestDTO> request = ArgumentCaptor.forClass(WmsOrderRequestDTO.class);
        verify(integrationApi).createMallShipment(request.capture());
        assertEquals("request-9", request.getValue().getRequestId());
        assertEquals(10L, request.getValue().getShopId());
        assertNotNull(failed.getRequestJson());
        assertEquals(TradeWmsOutboundService.PENDING, failed.getStatus());
    }

    @Test
    void legacyRequestWithoutShopIsMovedToManualReviewInsteadOfRetried() {
        WmsOrderRequestDTO legacyRequest = new WmsOrderRequestDTO();
        legacyRequest.setTenantId(2009L);
        legacyRequest.setSourceModule("MALL");
        legacyRequest.setItems(List.of(new cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO()
                .setWarehouseCode("FG-A")));
        TradeWmsShipmentDO legacy = new TradeWmsShipmentDO().setId(9L).setOrderId(500L).setOrderNo("o500")
                .setShopId(null).setWarehouseId(201L).setWarehouseCode("FG-A")
                .setSourceOrderNo("M500-S0-W201").setRequestId("legacy-request")
                .setStatus(TradeWmsOutboundService.UNAVAILABLE).setEventVersion(1L).setRetryCount(1)
                .setRequestJson(cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString(
                        legacyRequest));
        when(shipmentMapper.selectRetryable(100)).thenReturn(List.of(legacy));

        assertEquals(1, service.retryAndSync(100));

        verifyNoInteractions(integrationApi);
        assertEquals(TradeWmsOutboundService.MANUAL_REVIEW, legacy.getStatus());
        assertTrue(legacy.getLastError().contains("店铺归属"));
        verify(shipmentMapper).updateById(legacy);
    }

    private static ProductSkuRespDTO productSku(long id, long wmsSkuId, long warehouseId, String unitName) {
        ProductPropertyValueDetailRespDTO unit = new ProductPropertyValueDetailRespDTO();
        unit.setPropertyName("单位");
        unit.setValueName(unitName);
        return new ProductSkuRespDTO().setId(id).setWmsSkuId(wmsSkuId).setWmsWarehouseId(warehouseId)
                .setProperties(List.of(unit));
    }

    private static WmsStockApplicationOptionsRespDTO.Sku wmsSku(long id, String material, String code, String unit) {
        return new WmsStockApplicationOptionsRespDTO.Sku().setItemId(id + 1000).setSkuId(id)
                .setMaterialCode(material).setMaterialName(material).setMaterialType("FINISHED_GOODS")
                .setSkuCode(code).setSkuName(code).setUnitName(unit);
    }

    private static WmsWarehouseReadRespDTO warehouse(long id, String code) {
        return new WmsWarehouseReadRespDTO().setId(id).setCode(code).setWarehouseType("FINISHED_GOODS").setStatus(0);
    }

    private static TradeOrderItemDO item(long id, long skuId, long shopId, int count, int price) {
        return new TradeOrderItemDO().setId(id).setSkuId(skuId).setShopId(shopId).setCount(count)
                .setPrice(price).setSpuName("商品-" + skuId).setProperties(List.of());
    }
}
