package cn.iocoder.txgy.module.trade.service.supplier;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.shop.ProductAdminShopScopeApi;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductAdminShopScopeRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminShopScopeServiceTest {

    private AdminShopScopeService service;
    private ProductAdminShopScopeApi api;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach
    void setup() {
        service = new AdminShopScopeService();
        api = mock(ProductAdminShopScopeApi.class);
        ReflectionTestUtils.setField(service, "scopeApi", api);
        TenantContextHolder.setTenantId(2009L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(new LoginUser()
                .setId(101L).setUserType(UserTypeEnum.ADMIN.getValue()));
    }

    @AfterEach
    void cleanup() {
        security.close();
        TenantContextHolder.clear();
    }

    @Test
    void restrictedAccountCanOnlyUseListedShop() {
        when(api.getScope(101L)).thenReturn(success(scope(false, Set.of(10L))));
        when(api.validateAuthorizedShop(101L, 10L)).thenReturn(success(true));

        assertEquals(101L, service.requireShop(10L).adminUserId());
        assertThrows(ServiceException.class, () -> service.requireShop(11L));
        verify(api, never()).validateAuthorizedShop(101L, 11L);
    }

    @Test
    void restrictedEmptyScopeNeverMeansAllShops() {
        when(api.getScope(101L)).thenReturn(success(scope(false, Set.of())));

        assertThrows(ServiceException.class, () -> service.requireShop(10L));
        verify(api, never()).validateAuthorizedShop(anyLong(), anyLong());
    }

    @Test
    void mismatchedTenantOrUserFailsClosed() {
        ProductAdminShopScopeRespDTO remote = scope(false, Set.of(10L));
        remote.setTenantId(1L);
        when(api.getScope(101L)).thenReturn(success(remote));
        assertThrows(ServiceException.class, () -> service.requireShop(10L));

        remote.setTenantId(2009L);
        remote.setAdminUserId(999L);
        assertThrows(ServiceException.class, () -> service.requireShop(10L));
        verify(api, never()).validateAuthorizedShop(anyLong(), anyLong());
    }

    @Test
    void unrestrictedGovernanceAccountCannotOperateSupplierShop() {
        when(api.getScope(101L)).thenReturn(success(scope(true, Set.of())));

        assertThrows(ServiceException.class, () -> service.requireShop(10L));
        verify(api, never()).validateAuthorizedShop(anyLong(), anyLong());
    }

    @Test
    void memberTokenCannotEnterAdminDomain() {
        security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(new LoginUser()
                .setId(101L).setUserType(UserTypeEnum.MEMBER.getValue()));

        assertThrows(ServiceException.class, service::current);
        verifyNoInteractions(api);
    }

    private static ProductAdminShopScopeRespDTO scope(boolean unrestricted, Set<Long> shops) {
        ProductAdminShopScopeRespDTO result = new ProductAdminShopScopeRespDTO();
        result.setTenantId(2009L);
        result.setAdminUserId(101L);
        result.setUnrestricted(unrestricted);
        result.setShopIds(shops);
        return result;
    }
}
