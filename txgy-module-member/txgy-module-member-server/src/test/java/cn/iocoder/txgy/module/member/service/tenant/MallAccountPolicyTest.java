package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.biz.system.tenant.TenantCommonApi;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantAuthProperties;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MallAccountPolicyTest {
    private MallTenantProperties tenants;
    private MallTenantAuthProperties auth;
    private TenantCommonApi tenantApi;
    private MallCustomerAffiliationService customers;
    private MallAccountPolicy policy;

    @BeforeEach void setup() {
        tenants = new MallTenantProperties(); auth = new MallTenantAuthProperties();
        tenantApi = mock(TenantCommonApi.class); customers = mock(MallCustomerAffiliationService.class);
        when(tenantApi.validTenant(anyLong())).thenReturn(CommonResult.success(true));
        policy = policy(true); TenantContextHolder.clear();
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void pictureRegistrationCanUseOnlyTheExistingLegacyTenantWhenDirectoryIsOff() {
        assertEquals(1L, policy.resolve(null, true));
        assertEquals(1L, policy.resolve(1L, false));
        verify(customers).validateRegistration(1L);
        assertThrows(ServiceException.class, () -> policy.resolve(9001L, true));
        verify(tenantApi, never()).validTenant(9001L);
    }
    @Test void legacyTenantStillRequiresMatchingHeaderAndDoesNotChangeContext() {
        TenantContextHolder.setTenantId(2L);
        assertEquals(AUTH_TENANT_MISMATCH.getCode(), assertThrows(ServiceException.class, () -> policy.resolve(null, true)).getCode());
        assertEquals(2L, TenantContextHolder.getTenantId()); verifyNoInteractions(customers);
    }
    @Test void pictureFeatureOffDoesNotReadTenantsOrAuthorizeOldRegistrationThroughNewApi() {
        policy = policy(false);
        assertFalse(policy.isEnabled());
        assertEquals(MALL_TENANT_UNAVAILABLE.getCode(), assertThrows(ServiceException.class, () -> policy.resolve(1L, true)).getCode());
        assertThrows(ServiceException.class, () -> policy.resolve(1L, false));
        assertDoesNotThrow(policy::rejectLegacyRegistration);
        verifyNoInteractions(tenantApi, customers);
    }
    @Test void partialTenantAndAuthRolloutsFailClosedInBothDirections() {
        tenants.setEnabled(true);
        assertThrows(ServiceException.class, () -> policy.resolve(1L, true));
        tenants.setEnabled(false); auth.setEnabled(true);
        assertThrows(ServiceException.class, () -> policy.resolve(1L, false));
        verifyNoInteractions(tenantApi, customers);
    }
    @Test void legacyTenantDisabledOrUnknownCannotRegisterOrLogin() {
        when(tenantApi.validTenant(1L)).thenReturn(CommonResult.success(false));
        assertThrows(ServiceException.class, () -> policy.resolve(null, true));
        when(tenantApi.validTenant(1L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> policy.resolve(null, false));
        verifyNoInteractions(customers);
    }
    @Test void enabledDirectoryRequiresExplicitRegistrationTenant() {
        enableDirectory();
        assertThrows(ServiceException.class, () -> policy.resolve(null, true));
        assertEquals(9001L, policy.resolve(9001L, true));
        verify(customers).validateRegistration(9001L);
        verify(tenantApi, never()).validTenant(1L);
    }
    @Test void imagePolicyDoesNotDependOnSmsTemplateOrSmsHmacConfiguration() {
        enableDirectory(); auth.setSmsTemplateCode(""); auth.setHmacSecret("");
        assertEquals(9001L, policy.resolve(9001L, true));
    }
    @Test void missingPublicTenantNeverFallsBackToTenantOne() {
        enableDirectory(); tenants.setPublicTenantId(null);
        assertThrows(ServiceException.class, () -> policy.resolve(null, true));
        verifyNoInteractions(tenantApi, customers);
    }
    @Test void registrationRequiresBothPublicVisibilityAndItsSeparatePermission() {
        enableDirectory();
        assertEquals(9002L, policy.resolve(9002L, false));
        assertThrows(ServiceException.class, () -> policy.resolve(9002L, true));
        assertThrows(ServiceException.class, () -> policy.resolve(9003L, false));
        assertThrows(ServiceException.class, () -> policy.resolve(9999L, true));
        verifyNoInteractions(customers);
    }
    @Test void disabledListedTenantCannotBeUsedEvenForPasswordLogin() {
        enableDirectory(); when(tenantApi.validTenant(9002L)).thenReturn(CommonResult.success(false));
        assertThrows(ServiceException.class, () -> policy.resolve(9002L, false));
        verifyNoInteractions(customers);
    }
    @Test void unavailablePublicTenantClosesDirectoryInsteadOfFallingBack() {
        enableDirectory(); when(tenantApi.validTenant(9001L)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(ServiceException.class, () -> policy.resolve(null, true));
        verifyNoInteractions(customers);
    }
    @Test void explicitRegistrationTenantMustMatchCurrentHeader() {
        enableDirectory(); TenantContextHolder.setTenantId(9002L);
        assertEquals(AUTH_TENANT_MISMATCH.getCode(), assertThrows(ServiceException.class, () -> policy.resolve(9001L, true)).getCode());
        verifyNoInteractions(customers);
    }
    @Test void customerAffiliationMustBeReadyForRegistrationButNotForLogin() {
        doThrow(exception(MALL_TENANT_UNAVAILABLE)).when(customers).validateRegistration(1L);
        assertThrows(ServiceException.class, () -> policy.resolve(1L, true));
        assertEquals(1L, policy.resolve(1L, false));
        verify(customers, times(1)).validateRegistration(1L);
    }
    @Test void enabledPicturePolicyExplicitlyRejectsLegacyAccountCreation() {
        assertTrue(policy.isEnabled());
        assertEquals(MallAccountPolicy.IMAGE_REQUIRED.getCode(), assertThrows(ServiceException.class, policy::rejectLegacyRegistration).getCode());
    }
    @Test void normalizationAcceptsSixToTwentyCharactersAndUsesAsciiCaseRules() {
        assertEquals("buyer1", MallAccountPolicy.normalize(" BuYeR1 "));
        assertEquals("a".repeat(20), MallAccountPolicy.normalize("A".repeat(20)));
        assertEquals("buyer_1", MallAccountPolicy.normalize("Buyer_1"));
    }
    @Test void malformedNamesAndPhoneShapedNamesCannotBecomeLoginNames() {
        for (String bad : new String[] {null, "", "short", "a".repeat(21), "19900000000", "_buyer1", "buyer-1", "采购商123", "a buyer1"}) {
            assertEquals(MallAccountPolicy.INVALID_ACCOUNT.getCode(), assertThrows(ServiceException.class, () -> MallAccountPolicy.normalize(bad)).getCode());
        }
    }

    private MallAccountPolicy policy(boolean enabled) {
        return new MallAccountPolicy(tenants, auth, new MallTenantCatalogService(tenants, tenantApi, 1L), customers, tenantApi, 1L, enabled);
    }
    private void enableDirectory() {
        tenants.setEnabled(true); auth.setEnabled(true); tenants.setPublicTenantId(9001L);
        tenants.setEntries(List.of(entry(9001L, true, true), entry(9002L, true, false), entry(9003L, false, true)));
    }
    private static MallTenantProperties.Entry entry(long id, boolean visible, boolean registration) {
        return new MallTenantProperties.Entry().setTenantId(id).setName("isolated " + id)
                .setPublicVisible(visible).setRegistrationEnabled(registration);
    }
}
