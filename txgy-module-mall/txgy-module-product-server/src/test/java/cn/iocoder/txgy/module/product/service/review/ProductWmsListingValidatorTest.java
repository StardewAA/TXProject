package cn.iocoder.txgy.module.product.service.review;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsWarehouseReadRespDTO;
import cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductWmsListingValidatorTest {

    private WmsStockReadApi wmsStockReadApi;
    private ProductWmsListingValidator validator;

    @BeforeEach
    void setUp() {
        wmsStockReadApi = mock(WmsStockReadApi.class);
        validator = new ProductWmsListingValidator(wmsStockReadApi);
    }

    @Test
    void acceptsEnabledFinishedGoodsSkuAndWarehouseWithoutInventoryBalance() {
        stubMasterData("FINISHED_GOODS", "FINISHED_GOODS", 0);

        assertDoesNotThrow(() -> validator.validate(List.of(productSku(11L, 22L))));

        verify(wmsStockReadApi).getWarehouses();
        verify(wmsStockReadApi).getSkuOptions();
        verifyNoMoreInteractions(wmsStockReadApi);
    }

    @Test
    void rejectsMissingOrPartialMappingBeforeCallingWms() {
        assertThrows(ServiceException.class, () -> validator.validate(List.of(productSku(null, 22L))));
        assertThrows(ServiceException.class, () -> validator.validate(List.of(productSku(11L, null))));
        assertThrows(ServiceException.class, () -> validator.validate(List.of(productSku(0L, 22L))));
        verifyNoInteractions(wmsStockReadApi);
    }

    @Test
    void rejectsRawMaterialDisabledWarehouseAndUnknownIds() {
        stubMasterData("RAW_MATERIAL", "FINISHED_GOODS", 0);
        assertThrows(ServiceException.class, () -> validator.validate(List.of(productSku(11L, 22L))));

        reset(wmsStockReadApi);
        stubMasterData("FINISHED_GOODS", "FINISHED_GOODS", 1);
        assertThrows(ServiceException.class, () -> validator.validate(List.of(productSku(11L, 22L))));

        reset(wmsStockReadApi);
        stubMasterData("FINISHED_GOODS", "FINISHED_GOODS", 0);
        assertThrows(ServiceException.class, () -> validator.validate(List.of(productSku(99L, 22L))));
        assertThrows(ServiceException.class, () -> validator.validate(List.of(productSku(11L, 88L))));
    }

    @Test
    void failsClosedWhenWmsCannotBeRead() {
        when(wmsStockReadApi.getWarehouses()).thenThrow(new IllegalStateException("offline"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> validator.validate(List.of(productSku(11L, 22L))));

        assertEquals(503, exception.getCode());
        assertTrue(exception.getMessage().contains("WMS 主数据暂时无法核验"));
        verify(wmsStockReadApi, never()).getSkuOptions();
    }

    private void stubMasterData(String materialType, String warehouseType, Integer warehouseStatus) {
        WmsWarehouseReadRespDTO warehouse = new WmsWarehouseReadRespDTO();
        warehouse.setId(22L);
        warehouse.setWarehouseType(warehouseType);
        warehouse.setStatus(warehouseStatus);
        WmsStockApplicationOptionsRespDTO.Sku sku = new WmsStockApplicationOptionsRespDTO.Sku();
        sku.setItemId(101L);
        sku.setSkuId(11L);
        sku.setMaterialType(materialType);
        when(wmsStockReadApi.getWarehouses()).thenReturn(CommonResult.success(List.of(warehouse)));
        when(wmsStockReadApi.getSkuOptions()).thenReturn(CommonResult.success(List.of(sku)));
    }

    private static ProductSkuDO productSku(Long wmsSkuId, Long warehouseId) {
        return new ProductSkuDO().setId(1L).setWmsSkuId(wmsSkuId).setWmsWarehouseId(warehouseId);
    }
}
