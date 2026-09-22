package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.biz.system.tenant.TenantCommonApi;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.framework.tenant.config.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MallAuthPolicyTest {
    private MallTenantProperties tenants;
    private MallTenantAuthProperties auth;
    private TenantCommonApi api;
    private MallAuthPolicy policy;
    private MallCustomerAffiliationService customers;
    @BeforeEach void setup() {
        tenants = new MallTenantProperties(); auth = new MallTenantAuthProperties(); api = mock(TenantCommonApi.class);
        tenants.setEnabled(true); tenants.setPublicTenantId(9001L);
        tenants.setEntries(List.of(entry(9001L, true), entry(9002L, false)));
        auth.setEnabled(true); auth.setHmacSecret("isolated-test-secret-never-use-in-production"); auth.setSmsTemplateCode("isolated");
        when(api.validTenant(anyLong())).thenReturn(CommonResult.success(true));
        customers = mock(MallCustomerAffiliationService.class);
        policy = new MallAuthPolicy(tenants, auth, new MallTenantCatalogService(tenants, api, 1L), customers);
        TenantContextHolder.clear();
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    private static MallTenantProperties.Entry entry(long id, boolean registration) {
        return new MallTenantProperties.Entry().setTenantId(id).setName("isolated " + id)
                .setPublicVisible(true).setRegistrationEnabled(registration);
    }
    @Test void disabledDoesNotQueryDirectory() {
        auth.setEnabled(false); assertThrows(ServiceException.class, () -> policy.resolve(null, true)); verifyNoInteractions(api);
    }
    @Test void directoryDisabledCannotBeOverriddenByAuthFlag() {
        tenants.setEnabled(false); assertThrows(ServiceException.class, () -> policy.resolve(9001L, false)); verifyNoInteractions(api);
    }
    @Test void missingTenantDefaultsOnlyToExplicitPublicTenant() { assertEquals(9001L, policy.resolve(null, true)); }
    @Test void registrationPreflightChecksCustomerOwnerAndPropagatesUnavailability() {
        when(customers.validateRegistration(9001L)).thenThrow(cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE));
        assertThrows(ServiceException.class, () -> policy.resolve(null, true));
        verify(customers).validateRegistration(9001L);
    }
    @Test void loginDoesNotDependOnCustomerOwnerAvailability() {
        assertEquals(9001L, policy.resolve(null, false)); verifyNoInteractions(customers);
    }
    @Test void whitelistDoesNotAutomaticallyGrantRegistration() {
        assertEquals(9002L, policy.resolve(9002L, false));
        assertThrows(ServiceException.class, () -> policy.resolve(9002L, true));
    }
    @Test void headerBodyMismatchRejected() {
        TenantContextHolder.setTenantId(9002L); assertThrows(ServiceException.class, () -> policy.resolve(null, false));
    }
    @Test void missingPublicTenantNeverFallsBackToOne() {
        tenants.setPublicTenantId(null); assertThrows(ServiceException.class, () -> policy.resolve(null, true));
    }
    @Test void unlistedHiddenOrDisabledTenantRejected() {
        assertThrows(ServiceException.class, () -> policy.resolve(9999L, false));
        when(api.validTenant(9002L)).thenReturn(CommonResult.success(false));
        assertThrows(ServiceException.class, () -> policy.resolve(9002L, false));
        tenants.getEntries().get(0).setPublicVisible(false);
        assertThrows(ServiceException.class, () -> policy.resolve(9001L, true));
    }
    @Test void shortSecretMissingTemplateAndUnsafeLimitsFailClosed() {
        auth.setHmacSecret("short"); assertThrows(ServiceException.class, policy::requireConfigured);
        auth.setHmacSecret("isolated-test-secret-never-use-in-production"); auth.setSmsTemplateCode("");
        assertThrows(ServiceException.class, policy::requireConfigured);
        auth.setSmsTemplateCode("isolated"); auth.setMaxAttempts(999);
        assertThrows(ServiceException.class, policy::requireConfigured);
    }
    @Test void expiryOrDirectoryFailureCannotAuthorizeConsume() {
        when(api.validTenant(9001L)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(ServiceException.class, () -> policy.resolve(9001L, false));
    }
}
