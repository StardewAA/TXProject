package cn.iocoder.txgy.module.product.service.sku.adapter;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsStockRespDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductWmsInventoryDatabaseAdapterTest {

    @Test
    void readsExactMappedWmsBalance() {
        WmsStockReadApi api = mock(WmsStockReadApi.class);
        WmsStockRespDTO stock = new WmsStockRespDTO();
        stock.setSkuId(11L);
        stock.setWarehouseId(22L);
        stock.setQuantity(BigDecimal.valueOf(7));
        when(api.getStockPage(any())).thenReturn(success(new PageResult<>(List.of(stock), 1L)));
        ProductWmsInventoryDatabaseAdapter adapter = new ProductWmsInventoryDatabaseAdapter(api);

        var snapshot = adapter.getSnapshot(11L, 22L);

        assertTrue(snapshot.exists());
        assertEquals(7, snapshot.availableStock());
        assertEquals(BigDecimal.ZERO, snapshot.lockedQuantity());
    }

    @Test
    void rejectsAResponseForAnotherWarehouse() {
        WmsStockReadApi api = mock(WmsStockReadApi.class);
        WmsStockRespDTO stock = new WmsStockRespDTO();
        stock.setSkuId(11L);
        stock.setWarehouseId(99L);
        stock.setQuantity(BigDecimal.TEN);
        when(api.getStockPage(any())).thenReturn(success(new PageResult<>(List.of(stock), 1L)));
        ProductWmsInventoryDatabaseAdapter adapter = new ProductWmsInventoryDatabaseAdapter(api);

        ServiceException ex = assertThrows(ServiceException.class, () -> adapter.getSnapshot(11L, 22L));

        assertEquals(503, ex.getCode());
    }

    @Test
    void neverFabricatesReservationState() {
        WmsStockReadApi api = mock(WmsStockReadApi.class);
        ProductWmsInventoryDatabaseAdapter adapter = new ProductWmsInventoryDatabaseAdapter(api);

        assertTrue(adapter.getReservationSummaries(List.of("ORDER-1")).isEmpty());
        verifyNoInteractions(api);
    }
}
