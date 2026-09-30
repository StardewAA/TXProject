package cn.iocoder.txgy.module.trade.service.delivery;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.framework.delivery.config.TradeExpressProperties;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.ExpressClient;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.ExpressClientFactory;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressSnapshotRespDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.client.dto.ExpressTrackRespDTO;
import cn.iocoder.txgy.module.trade.framework.delivery.core.enums.ExpressClientEnum;
import cn.iocoder.txgy.module.trade.service.erp.ErpInventoryModels.Entry;
import cn.iocoder.txgy.module.trade.service.erp.TradeErpInventoryService;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeOrderLogisticsServiceTest {
    TradeOrderMapper orders = mock(TradeOrderMapper.class);
    TradeErpInventoryService erp = mock(TradeErpInventoryService.class);
    DeliveryExpressService carriers = mock(DeliveryExpressService.class);
    ExpressClientFactory clients = mock(ExpressClientFactory.class);
    ExpressClient client = mock(ExpressClient.class);
    TradeLogisticsQueryCache cache = mock(TradeLogisticsQueryCache.class);
    TradeExpressProperties properties = new TradeExpressProperties();
    TradeOrderLogisticsService service;
    TradeOrderDO order;
    LocalDateTime now = LocalDateTime.of(2026, 9, 17, 12, 0);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        order = new TradeOrderDO().setId(2L).setUserId(3L).setStatus(20).setDeliveryType(1)
                .setLogisticsId(4L).setLogisticsNo("TEST123456").setReceiverMobile("13800000000");
        when(orders.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(call ->
                Objects.equals(TenantContextHolder.getTenantId(), 1L) ? 1L : 0L);
        when(orders.selectByIdAndUserId(2L, 3L)).thenReturn(order);
        when(erp.list(2L)).thenReturn(List.of());
        when(carriers.getDeliveryExpress(4L)).thenReturn(new DeliveryExpressDO().setId(4L).setName("德邦").setCode("debangwuliu"));
        service = new TradeOrderLogisticsService(orders, erp, carriers, properties, clients, cache);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    void enable() {
        properties.setQueryEnabled(true).setClient(ExpressClientEnum.KD_100)
                .setKd100(new TradeExpressProperties.Kd100Config().setCustomer("test-customer").setKey("test-key"));
        when(clients.getDefaultExpressClient()).thenReturn(client);
        when(cache.query(anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            Supplier<ExpressSnapshotRespDTO> loader = call.getArgument(3);
            return new TradeLogisticsQueryCache.Result().setSnapshot(loader.get()).setQueriedTime(now).setRefreshAfterSeconds(1800);
        });
        when(client.getSnapshot(any())).thenReturn(new ExpressSnapshotRespDTO());
    }
    Entry entry(String operation, String status) {
        return new Entry("test-erp", 2L, "test-order", operation, "key", "pending", status, "private provider body", 0,
                null, 4L, "CONFIRMED".equals(status) ? "TEST123456" : "ERP123456", List.of(), now);
    }
    @Test void disabledShowsKnownWaybillWithoutAnyExternalCall() {
        var result = service.getForBuyer(2L, 3L);
        assertEquals("NOT_CONFIGURED", result.getQueryStatus());
        assertEquals("德邦", result.getCarrierName()); assertEquals("TEST123456", result.getTrackingNo());
        assertTrue(result.getEvents().isEmpty()); verifyNoInteractions(cache, clients);
    }
    @Test void configuredCredentialsDoNotImplicitlyEnablePaidQueries() {
        enable(); properties.setQueryEnabled(false);
        assertEquals("NOT_CONFIGURED", service.getForBuyer(2L, 3L).getQueryStatus()); verifyNoInteractions(cache);
    }
    @Test void incompleteCredentialsFailClosed() {
        enable(); properties.getKd100().setKey("");
        assertEquals("NOT_CONFIGURED", service.getForBuyer(2L, 3L).getQueryStatus()); verifyNoInteractions(cache);
    }
    @Test void erpPendingShowsPreparedWaybillWithoutProvider() {
        enable(); when(erp.list(2L)).thenReturn(List.of(entry("OUTBOUND", "PENDING")));
        var result = service.getForBuyer(2L, 3L);
        assertEquals("ERP_PENDING", result.getQueryStatus()); assertEquals("ERP123456", result.getTrackingNo());
        assertFalse(result.getMessage().contains("private")); verifyNoInteractions(cache);
    }
    @Test void erpRejectedNeverQueriesProvider() {
        enable(); when(erp.list(2L)).thenReturn(List.of(entry("OUTBOUND", "REJECTED")));
        assertEquals("ERP_REJECTED", service.getForBuyer(2L, 3L).getQueryStatus()); verifyNoInteractions(cache);
    }
    @Test void newOrderCannotBypassErpWithPrefilledShippedStatus() {
        enable(); when(erp.list(2L)).thenReturn(List.of(entry("RESERVE", "CONFIRMED")));
        assertEquals("ERP_PENDING", service.getForBuyer(2L, 3L).getQueryStatus()); verifyNoInteractions(cache);
    }
    @Test void confirmedOutboundAllowsQuery() {
        enable(); when(erp.list(2L)).thenReturn(List.of(entry("OUTBOUND", "CONFIRMED")));
        assertEquals("NO_EVENTS", service.getForBuyer(2L, 3L).getQueryStatus()); verify(client).getSnapshot(any());
    }
    @Test void simulatedErpNeverSendsSyntheticWaybillToRealProvider() {
        enable(); when(erp.list(2L)).thenReturn(List.of(new Entry("test-mock", 2L, "test-order", "OUTBOUND", "key", "mock",
                "CONFIRMED", "simulated", 0, "MOCK-1", 4L, "TEST123456", List.of(), now)));
        assertEquals("NOT_CONFIGURED", service.getForBuyer(2L, 3L).getQueryStatus()); verifyNoInteractions(cache);
    }
    @Test void confirmedOutboundMustMatchActualOrderWaybill() {
        enable(); when(erp.list(2L)).thenReturn(List.of(entry("OUTBOUND", "CONFIRMED")));
        order.setLogisticsNo("ALTERED123456");
        assertEquals("ERP_PENDING", service.getForBuyer(2L, 3L).getQueryStatus()); verifyNoInteractions(cache);
    }
    @Test void noTrackingIsDistinctFromNoConfiguration() {
        order.setLogisticsNo(" ");
        assertEquals("NO_TRACKING", service.getForBuyer(2L, 3L).getQueryStatus()); verifyNoInteractions(cache);
    }
    @Test void noCarrierIsSafeNotAnUnhandledError() {
        enable(); when(carriers.getDeliveryExpress(4L)).thenReturn(null);
        assertEquals("NO_TRACKING", service.getForBuyer(2L, 3L).getQueryStatus()); verifyNoInteractions(cache);
    }
    @Test void pickupOrderDoesNotQuery() {
        order.setDeliveryType(2);
        assertEquals("NOT_APPLICABLE", service.getForBuyer(2L, 3L).getQueryStatus()); verifyNoInteractions(cache);
    }
    @Test void undeliveredAndCanceledAreNotTransportFacts() {
        order.setStatus(10); assertEquals("NOT_SHIPPED", service.getForBuyer(2L, 3L).getQueryStatus());
        order.setStatus(40); assertEquals("ORDER_CANCELED", service.getForBuyer(2L, 3L).getQueryStatus()); verifyNoInteractions(cache);
    }
    @Test void canceledOrderWinsOverObsoletePendingOutbound() {
        enable(); order.setStatus(40); when(erp.list(2L)).thenReturn(List.of(entry("OUTBOUND", "PENDING")));
        assertEquals("ORDER_CANCELED", service.getForBuyer(2L, 3L).getQueryStatus());
        verifyNoInteractions(erp, cache, carriers);
    }
    @Test void anotherBuyerFailsBeforeErpOrCache() {
        assertThrows(RuntimeException.class, () -> service.getForBuyer(2L, 9L)); verifyNoInteractions(erp, cache, carriers);
    }
    @Test void sameUserIdAcrossTenantDoesNotAuthorize() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class, () -> service.getForBuyer(2L, 3L)); verifyNoInteractions(erp, cache, carriers);
    }
    @Test void evenWarmCacheCannotSkipOwnershipCheck() {
        enable(); service.getForBuyer(2L, 3L); reset(cache);
        when(orders.selectByIdAndUserId(2L, 3L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.getForBuyer(2L, 3L)); verifyNoInteractions(cache);
    }
    @Test void providerFailureIsSanitizedAndHasCooldown() {
        enable(); doReturn(new TradeLogisticsQueryCache.Result().setFailed(true).setRefreshAfterSeconds(30))
                .when(cache).query(anyString(), anyString(), anyString(), any());
        var result = service.getForBuyer(2L, 3L);
        assertEquals("PROVIDER_ERROR", result.getQueryStatus()); assertEquals(30, result.getRefreshAfterSeconds());
        assertTrue(result.getEvents().isEmpty()); assertNull(result.getUpdatedTime());
    }
    @Test void emptyResultIsNotClaimedAsCollected() {
        enable(); var result = service.getForBuyer(2L, 3L);
        assertEquals("NO_EVENTS", result.getQueryStatus()); assertEquals("UNKNOWN", result.getTransportStatus());
    }
    @Test void eventsSortedDeduplicatedAndSignedDoesNotReceiveOrder() {
        enable(); var latest = new ExpressTrackRespDTO().setTime(now).setContent("承运商签收记录");
        when(client.getSnapshot(any())).thenReturn(new ExpressSnapshotRespDTO().setRawStatus("3").setEvents(List.of(
                new ExpressTrackRespDTO().setTime(now.minusDays(1)).setContent("运送途中"), latest, latest,
                new ExpressTrackRespDTO().setTime(null).setContent("时间未提供"))));
        var result = service.getForBuyer(2L, 3L);
        assertEquals("AVAILABLE", result.getQueryStatus()); assertEquals("SIGNED", result.getTransportStatus());
        assertEquals(3, result.getEvents().size()); assertEquals(now, result.getEvents().getFirst().getTime());
        assertNull(result.getEvents().getLast().getTime()); assertEquals("UNKNOWN", result.getEvents().getFirst().getStatus());
        assertEquals(20, order.getStatus()); verify(orders).selectByIdAndUserId(2L, 3L);
        verify(orders).selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)); verifyNoMoreInteractions(orders);
        verify(erp).list(2L); verifyNoMoreInteractions(erp);
    }
    @Test void providerUnknownStatusAndSignedTextDoNotGuess() {
        enable(); when(client.getSnapshot(any())).thenReturn(new ExpressSnapshotRespDTO().setRawStatus("999")
                .setEvents(List.of(new ExpressTrackRespDTO().setContent("此前显示签收，正在核实"))));
        assertEquals("UNKNOWN", service.getForBuyer(2L, 3L).getTransportStatus());
    }
    @Test void mapsOnlyDocumentedProviderStates() {
        assertEquals("PICKED_UP", TradeOrderLogisticsService.mapStatus("kd100", "1"));
        assertEquals("IN_TRANSIT", TradeOrderLogisticsService.mapStatus("kd100", "0"));
        assertEquals("OUT_FOR_DELIVERY", TradeOrderLogisticsService.mapStatus("kd100", "5"));
        assertEquals("RETURNED", TradeOrderLogisticsService.mapStatus("kd100", "4"));
        assertEquals("RETURNING", TradeOrderLogisticsService.mapStatus("kd100", "6"));
        assertEquals("EXCEPTION", TradeOrderLogisticsService.mapStatus("kd100", "14"));
        assertEquals("UNKNOWN", TradeOrderLogisticsService.mapStatus("kdniao", "0"));
        assertEquals("PICKED_UP", TradeOrderLogisticsService.mapStatus("kdniao", "1"));
        assertEquals("IN_TRANSIT", TradeOrderLogisticsService.mapStatus("kdniao", "2"));
        assertEquals("SIGNED", TradeOrderLogisticsService.mapStatus("kdniao", "3"));
        assertEquals("EXCEPTION", TradeOrderLogisticsService.mapStatus("kdniao", "4"));
        assertEquals("UNKNOWN", TradeOrderLogisticsService.mapStatus("other", "3"));
    }
    @Test void stripsBlankAndNullEventsWithoutInventingATimestamp() {
        var result = TradeOrderLogisticsService.normalize(Arrays.asList(null, new ExpressTrackRespDTO().setContent(" "),
                new ExpressTrackRespDTO().setContent(" real text ")), "kd100");
        assertEquals(1, result.size()); assertEquals("real text", result.getFirst().getContent()); assertNull(result.getFirst().getTime());
    }
}
