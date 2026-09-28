package cn.iocoder.txgy.module.product.service.review;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.shop.ProductShopService;
import cn.iocoder.txgy.module.wms.api.integration.WmsIntegrationApi;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsMaterialUpsertReqDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsWarehouseUpsertReqDTO;
import cn.iocoder.txgy.module.wms.api.stock.WmsStockReadApi;
import cn.iocoder.txgy.module.wms.api.stock.dto.WmsWarehouseReadRespDTO;
import cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductWmsListingProvisionerTest {

    private WmsIntegrationApi integrationApi;
    private WmsStockReadApi stockReadApi;
    private ProductSkuMapper skuMapper;
    private ProductSpuMapper spuMapper;
    private ProductWmsListingProvisioner provisioner;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(2009L);
        integrationApi = mock(WmsIntegrationApi.class);
        stockReadApi = mock(WmsStockReadApi.class);
        skuMapper = mock(ProductSkuMapper.class);
        spuMapper = mock(ProductSpuMapper.class);
        ProductShopService shopService = mock(ProductShopService.class);
        ProductEnterpriseMapper enterpriseMapper = mock(ProductEnterpriseMapper.class);
        when(shopService.getShop(20L)).thenReturn(new ProductShopDO().setId(20L).setEnterpriseId(30L));
        when(enterpriseMapper.selectById(30L)).thenReturn(new ProductEnterpriseDO().setId(30L)
                .setName("测试企业").setAddress("测试地址").setContactName("张三").setContactMobile("13800000000"));
        provisioner = new ProductWmsListingProvisioner(integrationApi, stockReadApi, skuMapper, spuMapper,
                shopService, enterpriseMapper);
        when(skuMapper.updateById(any(ProductSkuDO.class))).thenReturn(1);
        when(spuMapper.updateById(any(ProductSpuDO.class))).thenReturn(1);
        when(integrationApi.upsertMaterial(any())).thenReturn(CommonResult.success(accepted("MALL-SKU-101")));
        when(integrationApi.upsertWarehouse(any())).thenReturn(CommonResult.success(accepted("MALL-FG-30")));
        when(integrationApi.createMallOpeningReceipt(any())).thenReturn(CommonResult.success(accepted("WMS-MALL-OPENING")));
    }

    @AfterEach
    void cleanUp() {
        TenantContextHolder.clear();
    }

    @Test
    void createsMissingFinishedMaterialAndUsesExistingFinishedWarehouse() {
        when(stockReadApi.getWarehouses()).thenReturn(CommonResult.success(List.of(warehouse(88L, "FG-01"))));
        when(stockReadApi.getSkuOptions()).thenReturn(CommonResult.success(List.of(wmsSku(99L, "MALL-SKU-101"))));
        ProductSkuDO sku = sku(101L, "桶");

        provisioner.provision(product(), List.of(sku));

        assertEquals(99L, sku.getWmsSkuId());
        assertEquals(88L, sku.getWmsWarehouseId());
        verify(integrationApi, never()).upsertWarehouse(any());
        ArgumentCaptor<WmsMaterialUpsertReqDTO> request = ArgumentCaptor.forClass(WmsMaterialUpsertReqDTO.class);
        verify(integrationApi).upsertMaterial(request.capture());
        assertEquals("MALL-SKU-101", request.getValue().getMaterialCode());
        assertEquals("FINISHED_GOODS", request.getValue().getMaterialType());
        assertEquals("BUCKET", request.getValue().getUnitCode());
        assertEquals("MALL", request.getValue().getSourceModule());
        verify(skuMapper).updateById((ProductSkuDO) argThat((ProductSkuDO update) -> update.getId().equals(101L)
                && update.getWmsSkuId().equals(99L) && update.getWmsWarehouseId().equals(88L)));
    }

    @Test
    void createsEnterpriseDefaultWarehouseWhenTenantHasNoFinishedWarehouse() {
        when(stockReadApi.getWarehouses()).thenReturn(CommonResult.success(List.of()),
                CommonResult.success(List.of(warehouse(77L, "MALL-FG-30"))));
        when(stockReadApi.getSkuOptions()).thenReturn(CommonResult.success(List.of(wmsSku(66L, "MALL-SKU-101"))));

        provisioner.provision(product(), List.of(sku(101L, "件")));

        ArgumentCaptor<WmsWarehouseUpsertReqDTO> request = ArgumentCaptor.forClass(WmsWarehouseUpsertReqDTO.class);
        verify(integrationApi).upsertWarehouse(request.capture());
        assertEquals("MALL-FG-30", request.getValue().getWarehouseCode());
        assertEquals("测试企业默认成品仓", request.getValue().getName());
        assertEquals("FINISHED_GOODS", request.getValue().getWarehouseType());
    }

    @Test
    void keepsExistingCompleteMappingWithoutCallingWms() {
        ProductSkuDO sku = sku(101L, "件").setWmsSkuId(9L).setWmsWarehouseId(8L);

        provisioner.provision(product(), List.of(sku));

        verifyNoInteractions(integrationApi, stockReadApi, skuMapper);
    }

    @Test
    void rejectsUnitOutsideSharedDictionary() {
        when(stockReadApi.getWarehouses()).thenReturn(CommonResult.success(List.of(warehouse(88L, "FG-01"))));

        ServiceException error = assertThrows(ServiceException.class,
                () -> provisioner.provision(product(), List.of(sku(101L, "未知单位"))));

        assertTrue(error.getMessage().contains("WMS 单位字典"));
        verify(integrationApi, never()).upsertMaterial(any());
    }

    @Test
    void createsOpeningReceiptAndClearsMallCacheUntilWmsReceiptCompletes() {
        when(stockReadApi.getWarehouses()).thenReturn(CommonResult.success(List.of(warehouse(88L, "FG-01"))));
        when(stockReadApi.getSkuOptions()).thenReturn(CommonResult.success(List.of(wmsSku(99L, "MALL-SKU-101"))));
        ProductSkuDO sku = sku(101L, "件").setStock(12).setCostPrice(500);

        provisioner.provision(product(), List.of(sku));

        ArgumentCaptor<WmsOrderRequestDTO> request = ArgumentCaptor.forClass(WmsOrderRequestDTO.class);
        verify(integrationApi).createMallOpeningReceipt(request.capture());
        assertEquals("MALL", request.getValue().getSourceModule());
        assertEquals("MALL_OPENING_RECEIPT", request.getValue().getOrderType());
        assertEquals(20L, request.getValue().getShopId());
        assertEquals(1, request.getValue().getItems().size());
        assertEquals("12", request.getValue().getItems().getFirst().getRequiredQuantity().toPlainString());
        assertEquals("5.00", request.getValue().getItems().getFirst().getPrice().toPlainString());
        assertEquals(0, sku.getStock());
        verify(skuMapper).updateById((ProductSkuDO) argThat((ProductSkuDO row) -> row.getId().equals(101L)
                && Integer.valueOf(0).equals(row.getStock())));
        verify(spuMapper).updateById((ProductSpuDO) argThat((ProductSpuDO row) -> row.getId().equals(10L)
                && Integer.valueOf(0).equals(row.getStock())));
    }

    private static ProductSpuDO product() {
        ProductSpuDO product = new ProductSpuDO().setId(10L).setShopId(20L).setName("环氧富锌底漆")
                .setContentVersion(2L);
        product.setCreateTime(LocalDateTime.of(2026, 9, 23, 1, 0));
        return product;
    }

    private static ProductSkuDO sku(Long id, String unit) {
        return new ProductSkuDO().setId(id).setSpuId(10L).setBarCode("BAR-" + id).setWeight(20D)
                .setProperties(List.of(new ProductSkuDO.Property(1L, "包装规格", 2L, "20kg/桶"),
                        new ProductSkuDO.Property(3L, "计量单位", 4L, unit)));
    }

    private static WmsWarehouseReadRespDTO warehouse(Long id, String code) {
        return new WmsWarehouseReadRespDTO().setId(id).setCode(code).setStatus(0)
                .setWarehouseType("FINISHED_GOODS");
    }

    private static WmsStockApplicationOptionsRespDTO.Sku wmsSku(Long id, String materialCode) {
        return new WmsStockApplicationOptionsRespDTO.Sku().setSkuId(id).setMaterialCode(materialCode)
                .setMaterialType("FINISHED_GOODS").setSkuCode(materialCode + "-DEFAULT");
    }

    private static WmsIntegrationAcceptedRespDTO accepted(String target) {
        return new WmsIntegrationAcceptedRespDTO().setAccepted(true).setTargetOrderNo(target)
                .setBusinessStatus("SYNCED").setProcessedVersion(1L);
    }
}
