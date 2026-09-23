package cn.iocoder.txgy.module.product.service.portal;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalHotTenantRespVO;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalProductPageReqVO;
import cn.iocoder.txgy.module.product.controller.app.portal.vo.AppPortalProductRespVO;
import cn.iocoder.txgy.module.product.dal.mysql.portal.MallPortalCatalogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MallPortalCatalogServiceTest {

    private MallPortalCatalogMapper mapper;
    private MallPortalCatalogService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MallPortalCatalogMapper.class);
        service = new MallPortalCatalogService(mapper);
    }

    @Test
    void getProductsReturnsOnlyMappedPublicProjectionAndSkus() {
        AppPortalProductPageReqVO request = new AppPortalProductPageReqVO();
        request.setPageNo(2);
        request.setPageSize(10);
        request.setKeyword("  coating  ");
        request.setCategoryId(88L);
        request.setCategoryName("  Protective Materials  ");

        MallPortalCatalogMapper.PortalProductRow row = productRow();
        MallPortalCatalogMapper.PortalSkuRow sku = new MallPortalCatalogMapper.PortalSkuRow();
        sku.setSpuId(501L);
        sku.setSkuId(601L);
        sku.setSpuName("Protective Coating");
        sku.setPicUrl("sku.png");
        sku.setPrice(1299);
        request.setShopId(401L);
        when(mapper.countProducts(null, "coating", 88L, "Protective Materials", 401L)).thenReturn(1L);
        when(mapper.selectProducts(null, "coating", 88L, "Protective Materials", 401L, null, null, 10L, 10)).thenReturn(List.of(row));
        when(mapper.selectPublicSkus(List.of(501L))).thenReturn(List.of(sku));

        PageResult<AppPortalProductRespVO> result = service.getProducts(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        AppPortalProductRespVO product = result.getList().getFirst();
        assertEquals(2001L, product.getTenantId());
        assertEquals("alpha-mall", product.getTenantSlug());
        assertEquals(501L, product.getSpuId());
        assertTrue(product.getSaleable());
        assertEquals(601L, product.getSkus().getFirst().getSkuId());
        assertEquals("Protective Coating", product.getSkus().getFirst().getSkuName());
        assertEquals(1299, product.getSkus().getFirst().getPrice());
    }

    @Test
    void getTenantProductsNormalizesSlugAndRejectsUnknownTenant() {
        AppPortalProductPageReqVO request = new AppPortalProductPageReqVO();
        MallPortalCatalogMapper.PortalTenantRow tenant = tenantRow();
        when(mapper.selectTenantBySlug("alpha-mall")).thenReturn(tenant);
        when(mapper.countProducts("alpha-mall", null, null, null, null)).thenReturn(0L);

        PageResult<AppPortalProductRespVO> result = service.getTenantProducts(" Alpha-Mall ", request);

        assertEquals(0L, result.getTotal());
        verify(mapper).selectTenantBySlug("alpha-mall");
        verify(mapper, never()).selectPublicSkus(anyList());

        ServiceException invalid = assertThrows(ServiceException.class,
                () -> service.getTenant("../tenant"));
        assertEquals(404, invalid.getCode());
    }

    @Test
    void getTenantProductVerifiesStableTenantRouteAndReturnsOnlySaleableDetail() {
        MallPortalCatalogMapper.PortalTenantRow tenant = tenantRow();
        MallPortalCatalogMapper.PortalProductRow row = productRow();
        row.setDescription("<p>Approved public detail</p>");
        MallPortalCatalogMapper.PortalSkuRow sku = new MallPortalCatalogMapper.PortalSkuRow();
        sku.setSpuId(501L);
        sku.setSkuId(601L);
        sku.setSpuName("Protective Coating");
        sku.setPrice(1299);
        when(mapper.selectTenantBySlug("alpha-mall")).thenReturn(tenant);
        when(mapper.selectProductById("alpha-mall", 501L)).thenReturn(row);
        when(mapper.selectPublicSkus(List.of(501L))).thenReturn(List.of(sku));

        AppPortalProductRespVO detail = service.getTenantProduct(" Alpha-Mall ", 501L);

        assertEquals(2001L, detail.getTenantId());
        assertEquals("alpha-mall", detail.getTenantSlug());
        assertEquals("<p>Approved public detail</p>", detail.getDescription());
        assertEquals(601L, detail.getSkus().getFirst().getSkuId());

        when(mapper.selectProductById(null, 999L)).thenReturn(null);
        ServiceException hidden = assertThrows(ServiceException.class, () -> service.getProduct(999L));
        assertEquals(404, hidden.getCode());
    }

    @Test
    void getProductRejectsItemWithoutAnyPublicInStockSku() {
        when(mapper.selectProductById(null, 501L)).thenReturn(productRow());
        when(mapper.selectPublicSkus(List.of(501L))).thenReturn(List.of());

        ServiceException hidden = assertThrows(ServiceException.class, () -> service.getProduct(501L));

        assertEquals(404, hidden.getCode());
    }

    @Test
    void getHotTenantsUsesInclusiveWindowAndAssignsStableRanks() {
        MallPortalCatalogMapper.PortalHotTenantRow first = hotTenantRow(2001L, "alpha-mall", 95L);
        MallPortalCatalogMapper.PortalHotTenantRow second = hotTenantRow(2002L, "beta-mall", 75L);
        when(mapper.selectHotTenants(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(List.of(first, second));

        List<AppPortalHotTenantRespVO> result = service.getHotTenants("30D", 2);

        assertEquals(List.of(1, 2), result.stream().map(AppPortalHotTenantRespVO::getRank).toList());
        assertEquals(List.of(95L, 75L), result.stream()
                .map(AppPortalHotTenantRespVO::getValidQuantity).toList());
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(mapper).selectHotTenants(from.capture(), to.capture(), org.mockito.ArgumentMatchers.eq(2));
        assertEquals(29L, java.time.temporal.ChronoUnit.DAYS.between(from.getValue(), to.getValue()));
    }

    @Test
    void getHotTenantsRejectsUnsupportedWindowAndUnsafeLimit() {
        ServiceException window = assertThrows(ServiceException.class,
                () -> service.getHotTenants("365d", 20));
        assertEquals(400, window.getCode());
        ServiceException limit = assertThrows(ServiceException.class,
                () -> service.getHotTenants("7d", 101));
        assertEquals(400, limit.getCode());
    }

    @Test
    void getShopsReturnsAllPublicShopsAndRejectsHiddenDetail() {
        MallPortalCatalogMapper.PortalShopRow row = new MallPortalCatalogMapper.PortalShopRow();
        row.setTenantId(2001L); row.setTenantSlug("alpha-mall"); row.setTenantName("Alpha Mall");
        row.setEnterpriseId(301L); row.setEnterpriseName("Alpha Enterprise");
        row.setShopId(401L); row.setShopName("Alpha Shop"); row.setProductCount(35L);
        when(mapper.selectShops("coating")).thenReturn(List.of(row));
        when(mapper.selectShopById(401L)).thenReturn(row);

        assertEquals(1, service.getShops("  coating  ").size());
        assertEquals(35L, service.getShop(401L).getProductCount());
        ServiceException hidden = assertThrows(ServiceException.class, () -> service.getShop(999L));
        assertEquals(404, hidden.getCode());
    }

    private static MallPortalCatalogMapper.PortalProductRow productRow() {
        MallPortalCatalogMapper.PortalProductRow row = new MallPortalCatalogMapper.PortalProductRow();
        row.setTenantId(2001L);
        row.setTenantSlug("alpha-mall");
        row.setTenantName("Alpha Mall");
        row.setTenantLogo("tenant.png");
        row.setEnterpriseId(301L);
        row.setEnterpriseName("Alpha Enterprise");
        row.setShopId(401L);
        row.setShopName("Alpha Shop");
        row.setSpuId(501L);
        row.setSpuName("Protective Coating");
        row.setIntroduction("Public introduction");
        row.setPicUrl("spu.png");
        row.setCategoryId(88L);
        row.setPrice(1299);
        row.setSalesCount(9);
        return row;
    }

    private static MallPortalCatalogMapper.PortalTenantRow tenantRow() {
        MallPortalCatalogMapper.PortalTenantRow row = new MallPortalCatalogMapper.PortalTenantRow();
        row.setTenantId(2001L);
        row.setTenantSlug("alpha-mall");
        row.setTenantName("Alpha Mall");
        return row;
    }

    private static MallPortalCatalogMapper.PortalHotTenantRow hotTenantRow(Long tenantId, String slug,
                                                                            Long validQuantity) {
        MallPortalCatalogMapper.PortalHotTenantRow row = new MallPortalCatalogMapper.PortalHotTenantRow();
        row.setTenantId(tenantId);
        row.setTenantSlug(slug);
        row.setTenantName(slug);
        row.setValidQuantity(validQuantity);
        return row;
    }
}
