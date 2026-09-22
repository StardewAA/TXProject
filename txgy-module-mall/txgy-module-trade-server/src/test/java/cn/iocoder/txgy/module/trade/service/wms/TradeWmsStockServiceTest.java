package cn.iocoder.txgy.module.trade.service.wms;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.product.api.sku.ProductSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.txgy.module.product.api.property.dto.ProductPropertyValueDetailRespDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsStockRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TradeWmsStockServiceTest {

    private ProductSkuApi productSkuApi;
    private WmsStockReadApi wmsStockReadApi;
    private TradeWmsStockService service;

    @BeforeEach
    void setUp() {
        productSkuApi = mock(ProductSkuApi.class);
        wmsStockReadApi = mock(WmsStockReadApi.class);
        service = new TradeWmsStockService(productSkuApi, wmsStockReadApi);
    }

    @Test
    void rejectsMissingMapping() {
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(1L);
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(1L, sku));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateSkuCounts(Map.of(1L, 1)));
        assertTrue(ex.getMessage().contains("尚未配置 WMS"));
        verifyNoInteractions(wmsStockReadApi);
    }

    @Test
    void rejectsNoBalanceAsInsufficientWithoutClaimingMesAccepted() {
        stubMappedSku();
        when(wmsStockReadApi.getStockPage(any())).thenReturn(success(new PageResult<>(List.of(), 0L)));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateSkuCounts(Map.of(1L, 2)));
        assertTrue(ex.getMessage().contains("WMS 库存不足"));
        assertFalse(ex.getMessage().contains("MES"));
    }

    @Test
    void rejectsInsufficientBalance() {
        stubMappedSku();
        WmsStockRespDTO stock = new WmsStockRespDTO();
        stock.setSkuId(101L);
        stock.setWarehouseId(201L);
        stock.setUnitName("件");
        stock.setQuantity(BigDecimal.ONE);
        when(wmsStockReadApi.getStockPage(any())).thenReturn(success(new PageResult<>(List.of(stock), 1L)));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateSkuCounts(Map.of(1L, 2)));
        assertTrue(ex.getMessage().contains("需求=2"));
    }

    @Test
    void failsClosedWhenWmsUnavailable() {
        stubMappedSku();
        when(wmsStockReadApi.getStockPage(any())).thenThrow(new IllegalStateException("down"));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateSkuCounts(Map.of(1L, 1)));
        assertEquals(503, ex.getCode());
    }

    @Test
    void acceptsSufficientBalance() {
        stubMappedSku();
        WmsStockRespDTO stock = new WmsStockRespDTO();
        stock.setSkuId(101L);
        stock.setWarehouseId(201L);
        stock.setUnitName("件");
        stock.setQuantity(BigDecimal.TEN);
        when(wmsStockReadApi.getStockPage(any())).thenReturn(success(new PageResult<>(List.of(stock), 1L)));
        assertDoesNotThrow(() -> service.validateSkuCounts(Map.of(1L, 2)));
    }

    @Test
    void mergesDuplicateSkuQuantitiesBeforeCheckingWms() {
        stubMappedSku();
        WmsStockRespDTO stock = new WmsStockRespDTO();
        stock.setSkuId(101L);
        stock.setWarehouseId(201L);
        stock.setUnitName("件");
        stock.setQuantity(BigDecimal.valueOf(2));
        when(wmsStockReadApi.getStockPage(any())).thenReturn(success(new PageResult<>(List.of(stock), 1L)));
        TradeOrderItemDO first = new TradeOrderItemDO().setSkuId(1L).setCount(1);
        TradeOrderItemDO second = new TradeOrderItemDO().setSkuId(1L).setCount(2);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateAvailable(List.of(first, second)));

        assertTrue(ex.getMessage().contains("需求=3"));
        verify(wmsStockReadApi, times(1)).getStockPage(any());
    }

    @Test
    void failsClosedWhenWmsReturnsAnotherMapping() {
        stubMappedSku();
        WmsStockRespDTO stock = new WmsStockRespDTO();
        stock.setSkuId(999L);
        stock.setWarehouseId(201L);
        stock.setUnitName("件");
        stock.setQuantity(BigDecimal.TEN);
        when(wmsStockReadApi.getStockPage(any())).thenReturn(success(new PageResult<>(List.of(stock), 1L)));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateSkuCounts(Map.of(1L, 2)));
        assertEquals(503, ex.getCode());
        assertTrue(ex.getMessage().contains("映射不一致"));
    }

    private void stubMappedSku() {
        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setId(1L);
        sku.setWmsSkuId(101L);
        sku.setWmsWarehouseId(201L);
        ProductPropertyValueDetailRespDTO unit = new ProductPropertyValueDetailRespDTO();
        unit.setPropertyName("单位");
        unit.setValueName("件");
        sku.setProperties(List.of(unit));
        when(productSkuApi.getSkuMap(any())).thenReturn(Map.of(1L, sku));
    }

    @Test
    void rejectsUnknownUnitConversion() {
        stubMappedSku();
        WmsStockRespDTO stock = new WmsStockRespDTO();
        stock.setSkuId(101L);
        stock.setWarehouseId(201L);
        stock.setUnitName("箱");
        stock.setQuantity(BigDecimal.TEN);
        when(wmsStockReadApi.getStockPage(any())).thenReturn(success(new PageResult<>(List.of(stock), 1L)));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateSkuCounts(Map.of(1L, 1)));
        assertTrue(ex.getMessage().contains("计量单位不一致"));
    }
}
