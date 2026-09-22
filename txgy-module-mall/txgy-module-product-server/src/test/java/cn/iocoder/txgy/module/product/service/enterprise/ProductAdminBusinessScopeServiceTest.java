package cn.iocoder.txgy.module.product.service.enterprise;

import cn.iocoder.txgy.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseAdminMemberDO;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.ProductEnterpriseDO;
import cn.iocoder.txgy.module.product.dal.dataobject.shop.ProductShopDO;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseAdminMemberMapper;
import cn.iocoder.txgy.module.product.dal.mysql.enterprise.ProductEnterpriseMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopAdminAuthorizationMapper;
import cn.iocoder.txgy.module.product.dal.mysql.shop.ProductShopMapper;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductAdminBusinessScopeServiceTest {

    private static final long TENANT_ID = 2009L;
    private static final long ADMIN_USER_ID = 91L;
    private static final long ENTERPRISE_ID = 801L;
    private static final long SHOP_ID = 701L;

    private ProductAdminBusinessScopeService service;
    private PermissionCommonApi permissionApi;
    private ProductEnterpriseAdminMemberMapper enterpriseMemberMapper;
    private ProductShopAdminAuthorizationMapper shopAuthorizationMapper;
    private ProductEnterpriseMapper enterpriseMapper;
    private ProductShopMapper shopMapper;

    @BeforeEach
    void setUp() {
        permissionApi = mock(PermissionCommonApi.class);
        enterpriseMemberMapper = mock(ProductEnterpriseAdminMemberMapper.class);
        shopAuthorizationMapper = mock(ProductShopAdminAuthorizationMapper.class);
        enterpriseMapper = mock(ProductEnterpriseMapper.class);
        shopMapper = mock(ProductShopMapper.class);
        AdminUserApi adminUserApi = mock(AdminUserApi.class);

        AdminUserRespDTO admin = new AdminUserRespDTO();
        admin.setId(ADMIN_USER_ID);
        admin.setStatus(0);
        when(adminUserApi.getUser(ADMIN_USER_ID)).thenReturn(CommonResult.success(admin));
        when(enterpriseMemberMapper.selectList(any())).thenReturn(List.of());
        when(shopAuthorizationMapper.selectList(any())).thenReturn(List.of());

        service = new ProductAdminBusinessScopeService();
        ReflectionTestUtils.setField(service, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(service, "enterpriseMemberMapper", enterpriseMemberMapper);
        ReflectionTestUtils.setField(service, "shopAuthorizationMapper", shopAuthorizationMapper);
        ReflectionTestUtils.setField(service, "enterpriseMapper", enterpriseMapper);
        ReflectionTestUtils.setField(service, "shopMapper", shopMapper);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantManagerCannotUseUnrestrictedScopeAsMerchantAuthorization() {
        when(permissionApi.hasAnyRoles(ADMIN_USER_ID, "super_admin", "tenant_admin"))
                .thenReturn(CommonResult.success(true));

        ProductAdminBusinessScopeService.Scope scope = service.scope(ADMIN_USER_ID);
        assertTrue(scope.unrestricted());
        assertTrue(scope.shopIds().isEmpty());
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.requireMerchantShop(ADMIN_USER_ID, SHOP_ID));
        assertEquals(403, error.getCode());
    }

    @Test
    void explicitEnterpriseMemberCanOperateOnlyItsActiveQualifiedShop() {
        when(permissionApi.hasAnyRoles(ADMIN_USER_ID, "super_admin", "tenant_admin"))
                .thenReturn(CommonResult.success(false));
        ProductEnterpriseAdminMemberDO membership = new ProductEnterpriseAdminMemberDO();
        membership.setAdminUserId(ADMIN_USER_ID);
        membership.setEnterpriseId(ENTERPRISE_ID);
        membership.setScopeType(ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE);
        membership.setStatus(0);
        when(enterpriseMemberMapper.selectList(any())).thenReturn(List.of(membership));

        ProductEnterpriseDO enterprise = new ProductEnterpriseDO();
        enterprise.setId(ENTERPRISE_ID);
        enterprise.setTenantId(TENANT_ID);
        enterprise.setAuditStatus(1);
        when(enterpriseMapper.selectList(any())).thenReturn(List.of(enterprise));
        when(enterpriseMapper.selectById(ENTERPRISE_ID)).thenReturn(enterprise);

        ProductShopDO shop = new ProductShopDO();
        shop.setId(SHOP_ID);
        shop.setTenantId(TENANT_ID);
        shop.setEnterpriseId(ENTERPRISE_ID);
        shop.setStatus(0);
        when(shopMapper.selectList(any())).thenReturn(List.of(shop));
        when(shopMapper.selectById(SHOP_ID)).thenReturn(shop);

        assertEquals(SHOP_ID, service.requireMerchantShop(ADMIN_USER_ID, SHOP_ID).getId());
        assertTrue(service.isEnterpriseBusinessMember(ADMIN_USER_ID, ENTERPRISE_ID));
    }

    @Test
    void tenantManagerRemainsReviewerOnlyEvenWhenStaleBusinessRowsExist() {
        when(permissionApi.hasAnyRoles(ADMIN_USER_ID, "super_admin", "tenant_admin"))
                .thenReturn(CommonResult.success(true));
        ProductEnterpriseAdminMemberDO membership = new ProductEnterpriseAdminMemberDO();
        membership.setAdminUserId(ADMIN_USER_ID);
        membership.setEnterpriseId(ENTERPRISE_ID);
        membership.setScopeType(ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE);
        membership.setStatus(0);
        when(enterpriseMemberMapper.selectList(any())).thenReturn(List.of(membership));

        ProductEnterpriseDO enterprise = new ProductEnterpriseDO();
        enterprise.setId(ENTERPRISE_ID);
        enterprise.setTenantId(TENANT_ID);
        enterprise.setAuditStatus(1);
        when(enterpriseMapper.selectList(any())).thenReturn(List.of(enterprise));
        ProductShopDO shop = new ProductShopDO();
        shop.setId(SHOP_ID);
        shop.setTenantId(TENANT_ID);
        shop.setEnterpriseId(ENTERPRISE_ID);
        shop.setStatus(0);
        when(shopMapper.selectList(any())).thenReturn(List.of(shop));

        ProductAdminBusinessScopeService.Scope scope = service.scope(ADMIN_USER_ID);
        assertTrue(scope.unrestricted());
        assertEquals(java.util.Set.of(SHOP_ID), scope.shopIds());
        assertThrows(ServiceException.class, () -> service.requireMerchantShop(ADMIN_USER_ID, SHOP_ID));
    }

    @Test
    void tenantGovernanceCanReadDisabledShopResourceButCannotWriteIt() {
        when(permissionApi.hasAnyRoles(ADMIN_USER_ID, "super_admin", "tenant_admin"))
                .thenReturn(CommonResult.success(true));
        ProductShopDO disabled = new ProductShopDO();
        disabled.setId(SHOP_ID);
        disabled.setTenantId(TENANT_ID);
        disabled.setEnterpriseId(ENTERPRISE_ID);
        disabled.setStatus(1);
        when(shopMapper.selectById(SHOP_ID)).thenReturn(disabled);

        assertEquals(SHOP_ID, service.requireShopResource(ADMIN_USER_ID, SHOP_ID).getId());
        assertThrows(ServiceException.class, () -> service.requireShop(ADMIN_USER_ID, SHOP_ID));
        assertThrows(ServiceException.class, () -> service.requireMerchantShop(ADMIN_USER_ID, SHOP_ID));
    }

    @Test
    void explicitMerchantCanReadDisabledShopHistoryButCannotContinueWriting() {
        when(permissionApi.hasAnyRoles(ADMIN_USER_ID, "super_admin", "tenant_admin"))
                .thenReturn(CommonResult.success(false));
        ProductEnterpriseAdminMemberDO membership = new ProductEnterpriseAdminMemberDO();
        membership.setTenantId(TENANT_ID);
        membership.setAdminUserId(ADMIN_USER_ID);
        membership.setEnterpriseId(ENTERPRISE_ID);
        membership.setScopeType(ProductEnterpriseAdminMemberDO.SCOPE_ENTERPRISE);
        membership.setStatus(0);
        when(enterpriseMemberMapper.selectOne(any())).thenReturn(membership);

        ProductShopDO disabled = new ProductShopDO();
        disabled.setId(SHOP_ID);
        disabled.setTenantId(TENANT_ID);
        disabled.setEnterpriseId(ENTERPRISE_ID);
        disabled.setStatus(1);
        when(shopMapper.selectById(SHOP_ID)).thenReturn(disabled);

        assertSame(disabled, service.requireMerchantShopHistory(ADMIN_USER_ID, SHOP_ID));
        assertThrows(ServiceException.class, () -> service.requireMerchantShop(ADMIN_USER_ID, SHOP_ID));
    }

    @Test
    void tenantManagerCannotUseGovernanceRoleToReadMerchantQuoteHistory() {
        when(permissionApi.hasAnyRoles(ADMIN_USER_ID, "super_admin", "tenant_admin"))
                .thenReturn(CommonResult.success(true));

        assertThrows(ServiceException.class,
                () -> service.requireMerchantShopHistory(ADMIN_USER_ID, SHOP_ID));
    }
}
