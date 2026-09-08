package cn.iocoder.txgy.module.wms.service.inventory;

import cn.iocoder.txgy.module.wms.dal.dataobject.inventory.WmsInventoryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsInventoryMapper;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemSkuService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WmsInventoryReadServiceTest {
    private final WmsItemService items = mock(WmsItemService.class);
    private final WmsItemSkuService skus = mock(WmsItemSkuService.class);
    private final WmsInventoryMapper inventory = mock(WmsInventoryMapper.class);
    private final WmsInventoryReadService service = new WmsInventoryReadService(items, skus, inventory);

    @Test
    void sumsWarehousesWithoutMixingSpecificationsAndPreservesDecimals() {
        when(items.validateItemExists(1L)).thenReturn(WmsItemDO.builder().id(1L).name("管材").unit("米").build());
        when(skus.getItemSkuList(1L)).thenReturn(List.of(
                WmsItemSkuDO.builder().id(11L).itemId(1L).name("DN20").sellingPrice(new BigDecimal("12.3456")).build(),
                WmsItemSkuDO.builder().id(12L).itemId(1L).name("DN25").build()));
        when(inventory.selectListBySkuIds(List.of(11L, 12L))).thenReturn(List.of(
                WmsInventoryDO.builder().skuId(11L).warehouseId(1L).quantity(new BigDecimal("1.250")).build(),
                WmsInventoryDO.builder().skuId(11L).warehouseId(2L).quantity(new BigDecimal("2.125")).build()));
        var result = service.getItemInventory(1L);
        assertEquals(2, result.size());
        assertEquals(0, new BigDecimal("3.375").compareTo(result.get(0).getTotalQuantity()));
        assertEquals(new BigDecimal("12.3456"), result.get(0).getSellingPrice());
        assertEquals("米", result.get(0).getUnit());
        assertEquals("DN20", result.get(0).getSkuName());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(1).getTotalQuantity()));
        assertNull(result.get(1).getSellingPrice());
    }

    @Test
    void failedLookupIsNotReportedAsZeroStock() {
        when(items.validateItemExists(1L)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> service.getItemInventory(1L));
    }

    @Test
    void noSpecificationsReturnsEmptyList() {
        when(items.validateItemExists(1L)).thenReturn(WmsItemDO.builder().id(1L).build());
        when(skus.getItemSkuList(1L)).thenReturn(List.of());
        assertTrue(service.getItemInventory(1L).isEmpty());
    }
}
