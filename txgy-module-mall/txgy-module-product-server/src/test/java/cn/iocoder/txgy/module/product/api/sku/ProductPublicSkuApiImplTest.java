package cn.iocoder.txgy.module.product.api.sku;

import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.mysql.sku.ProductPublicSkuMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductPublicSkuApiImplTest {

    private ProductPublicSkuMapper mapper;
    private ProductPublicSkuApiImpl api;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductPublicSkuMapper.class);
        api = new ProductPublicSkuApiImpl();
        ReflectionTestUtils.setField(api, "mapper", mapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsOnlyServerResolvedOwnershipAndPublicFields() {
        ProductPublicSkuMapper.PublicSkuRow row = validRow();
        row.setProperties(List.of(new ProductSkuDO.Property(10L, "厚度", 11L, "50mm")));
        when(mapper.selectBySkuIds(java.util.Set.of(601L))).thenReturn(List.of(row));

        var result = api.getOwnershipList(List.of(601L, 601L));

        assertEquals(1, result.size());
        assertEquals(2001L, result.getFirst().getTenantId());
        assertEquals(301L, result.getFirst().getEnterpriseId());
        assertEquals(401L, result.getFirst().getShopId());
        assertEquals("厚度", result.getFirst().getProperties().getFirst().getPropertyName());
        assertTrue(result.getFirst().getSaleable());
        verify(mapper).selectBySkuIds(java.util.Set.of(601L));
    }

    @Test
    void invalidProductIsReturnedForOwnedCartDiagnosisButCannotBeSold() {
        ProductPublicSkuMapper.PublicSkuRow row = validRow();
        row.setReviewStatus(10);
        row.setSpuName("未审核的商品名");
        row.setPicUrl("https://private.invalid/draft.png");
        row.setPrice(987654);
        row.setStock(321);
        row.setProperties(List.of(new ProductSkuDO.Property(10L, "未审核属性", 11L, "未审核值")));
        when(mapper.selectBySkuIds(java.util.Set.of(601L))).thenReturn(List.of(row));

        var result = api.getOwnershipList(List.of(601L)).getFirst();

        assertFalse(result.getSaleable());
        assertEquals("商品审核未通过或已变更", result.getInvalidReason());
        assertNull(result.getSpuName());
        assertNull(result.getPicUrl());
        assertNull(result.getPrice());
        assertNull(result.getStock());
        assertTrue(result.getProperties().isEmpty());
    }

    @Test
    void logisticsSnapshotUsesOnlyServerResolvedPriceStockAndDeliveryFields() {
        ProductPublicSkuMapper.PublicSkuRow row = validRow();
        row.setDeliveryTypes(List.of(1, 2));
        row.setDeliveryTemplateId(701L);
        row.setWeight(12.5D);
        row.setVolume(0.75D);
        when(mapper.selectBySkuIds(java.util.Set.of(601L))).thenReturn(List.of(row));

        var result = api.getLogisticsList(List.of(601L)).getFirst();

        assertEquals(2001L, result.getTenantId());
        assertEquals(301L, result.getEnterpriseId());
        assertEquals(401L, result.getShopId());
        assertEquals(501L, result.getSpuId());
        assertEquals(601L, result.getSkuId());
        assertEquals(1200, result.getPrice());
        assertEquals(20, result.getStock());
        assertEquals(List.of(1, 2), result.getDeliveryTypes());
        assertEquals(701L, result.getDeliveryTemplateId());
        assertEquals(12.5D, result.getWeight());
        assertEquals(0.75D, result.getVolume());
        assertTrue(result.getSaleable());
        assertNull(result.getInvalidReason());
    }

    @Test
    void invalidLogisticsSnapshotKeepsOwnershipAndReasonButHidesSettlementFields() {
        ProductPublicSkuMapper.PublicSkuRow row = validRow();
        row.setReviewStatus(10);
        row.setPrice(987654);
        row.setStock(321);
        row.setDeliveryTypes(List.of(1));
        row.setDeliveryTemplateId(701L);
        row.setWeight(12.5D);
        row.setVolume(0.75D);
        when(mapper.selectBySkuIds(java.util.Set.of(601L))).thenReturn(List.of(row));

        var result = api.getLogisticsList(List.of(601L)).getFirst();

        assertEquals(2001L, result.getTenantId());
        assertEquals(301L, result.getEnterpriseId());
        assertEquals(401L, result.getShopId());
        assertEquals(501L, result.getSpuId());
        assertEquals(601L, result.getSkuId());
        assertFalse(result.getSaleable());
        assertEquals("商品审核未通过或已变更", result.getInvalidReason());
        assertNull(result.getPrice());
        assertNull(result.getStock());
        assertNull(result.getDeliveryTypes());
        assertNull(result.getDeliveryTemplateId());
        assertNull(result.getWeight());
        assertNull(result.getVolume());
    }

    @Test
    void logisticsEndpointDeclaresTenantIgnoreAndDoesNotClearSourceLoginUser() throws Exception {
        Method endpoint = ProductPublicSkuApiImpl.class
                .getMethod("getLogisticsList", java.util.Collection.class);
        assertNotNull(ProductPublicSkuApiImpl.class.getAnnotation(TenantIgnore.class));
        assertNotNull(endpoint.getAnnotation(TenantIgnore.class));

        LoginUser sourceLoginUser = new LoginUser().setId(316L).setTenantId(1L).setUserType(2);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(sourceLoginUser, null, List.of()));
        TenantContextHolder.setTenantId(1L);
        // 模拟方法级 @TenantIgnore 切面已进入；实现本身不得清理登录上下文或切换为目标租户。
        TenantContextHolder.setIgnore(true);
        when(mapper.selectBySkuIds(any())).thenAnswer(invocation -> {
            assertTrue(TenantContextHolder.isIgnore());
            assertSame(sourceLoginUser, SecurityFrameworkUtils.getLoginUser());
            return List.of(validRow());
        });

        var result = api.getLogisticsList(List.of(601L));

        assertEquals(1, result.size());
        assertTrue(TenantContextHolder.isIgnore());
        assertEquals(1L, TenantContextHolder.getTenantId());
        assertSame(sourceLoginUser, SecurityFrameworkUtils.getLoginUser());
    }

    @Test
    void logisticsContractAcceptsSkuIdsOnly() throws Exception {
        Method method = ProductPublicSkuApi.class
                .getMethod("getLogisticsList", java.util.Collection.class);
        assertEquals(1, method.getParameterCount());
        assertEquals(java.util.Collection.class, method.getParameterTypes()[0]);
    }

    @Test
    void rejectsMapperLeakFromPublicTenantOrUnexpectedSku() {
        ProductPublicSkuMapper.PublicSkuRow publicTenant = validRow();
        publicTenant.setTenantId(3000L);
        when(mapper.selectBySkuIds(java.util.Set.of(601L))).thenReturn(List.of(publicTenant));
        assertThrows(IllegalStateException.class, () -> api.getOwnershipList(List.of(601L)));

        ProductPublicSkuMapper.PublicSkuRow unexpected = validRow();
        unexpected.setSkuId(999L);
        when(mapper.selectBySkuIds(java.util.Set.of(601L))).thenReturn(List.of(unexpected));
        assertThrows(IllegalStateException.class, () -> api.getOwnershipList(List.of(601L)));
    }

    @Test
    void crossTenantSqlIsFailClosedToOpenDirectoryAndExcludes3000() throws Exception {
        Select select = ProductPublicSkuMapper.class
                .getMethod("selectBySkuIds", java.util.Collection.class).getAnnotation(Select.class);
        String sql = String.join(" ", select.value())
                .replace("&lt;", "<").replace("&gt;", ">");
        assertTrue(sql.contains("pt.open_status = 0"));
        assertTrue(sql.contains("pt.tenant_id <> 3000"));
        assertTrue(sql.contains("st.status = 0"));
        assertTrue(sql.contains("st.expire_time IS NULL OR st.expire_time > NOW()"));
        assertTrue(sql.contains("sku.price AS price"));
        assertTrue(sql.contains("spu.delivery_types AS deliveryTypes"));
        assertTrue(sql.contains("spu.delivery_template_id AS deliveryTemplateId"));
        assertTrue(sql.contains("sku.weight AS weight"));
        assertTrue(sql.contains("sku.volume AS volume"));
    }

    private static ProductPublicSkuMapper.PublicSkuRow validRow() {
        ProductPublicSkuMapper.PublicSkuRow row = new ProductPublicSkuMapper.PublicSkuRow();
        row.setTenantId(2001L);
        row.setTenantName("正式租户");
        row.setEnterpriseId(301L);
        row.setEnterpriseName("供应企业");
        row.setShopId(401L);
        row.setShopName("供应店铺");
        row.setSpuId(501L);
        row.setSpuName("防腐材料");
        row.setSkuId(601L);
        row.setPrice(1200);
        row.setStock(20);
        row.setSkuDeleted(false);
        row.setSpuDeleted(false);
        row.setSpuStatus(1);
        row.setReviewStatus(20);
        row.setContentVersion(2L);
        row.setApprovedVersion(2L);
        row.setShopDeleted(false);
        row.setShopStatus(0);
        row.setEnterpriseDeleted(false);
        row.setEnterpriseAuditStatus(1);
        return row;
    }
}
