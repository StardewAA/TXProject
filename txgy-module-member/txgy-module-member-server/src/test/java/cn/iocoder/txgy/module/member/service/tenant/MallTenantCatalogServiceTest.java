package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.biz.system.tenant.TenantCommonApi;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallTenantConfigRespVO;
import cn.iocoder.txgy.module.member.framework.tenant.config.MallTenantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MallTenantCatalogServiceTest {

    private MallTenantProperties properties;
    private TenantCommonApi tenantApi;
    private MallTenantCatalogService service;

    @BeforeEach
    void setUp() {
        properties = new MallTenantProperties();
        tenantApi = mock(TenantCommonApi.class);
        service = new MallTenantCatalogService(properties, tenantApi, 1L);
    }

    @Test
    void disabledIgnoresEvenMalformedConfigurationAndMakesNoRpcCall() {
        properties.setPublicTenantId(9001L).setEntries(List.of(entry(9001L, "公共商城", true)));
        AppMallTenantConfigRespVO result = service.getConfig();
        assertFalse(result.isEnabled());
        assertEquals(1L, result.getLegacyTenantId());
        assertNull(result.getPublicTenantId());
        assertTrue(result.getTenants().isEmpty());
        assertNoCapabilities(result);
        verifyNoInteractions(tenantApi);
    }

    @Test
    void onlyExplicitlyVisibleValidEntriesAreReturnedAndSystemTenantsAreNeverEnumerated() {
        properties.setEnabled(true).setPublicTenantId(9001L).setEntries(List.of(
                entry(9001L, "公共商城", true), entry(9002L, "内部租户", false),
                entry(9003L, "公开商城", true), entry(9004L, "已停用商城", true)));
        when(tenantApi.validTenant(9001L)).thenReturn(CommonResult.success(true));
        when(tenantApi.validTenant(9003L)).thenReturn(CommonResult.success(true));
        when(tenantApi.validTenant(9004L)).thenReturn(CommonResult.error(123, "内部状态详情"));

        AppMallTenantConfigRespVO result = service.getConfig();

        assertEquals(List.of(9001L, 9003L), result.getTenants().stream()
                .map(AppMallTenantConfigRespVO.Tenant::getTenantId).toList());
        assertEquals(List.of("公共商城", "公开商城"), result.getTenants().stream()
                .map(AppMallTenantConfigRespVO.Tenant::getName).toList());
        assertEquals(9001L, result.getPublicTenantId());
        verify(tenantApi, never()).validTenant(9002L);
        verify(tenantApi, never()).getTenantIdList();
    }

    @Test
    void enabledWithoutPublicTenantStillReturnsOnlyValidatedRealTenants() {
        properties.setEnabled(true).setEntries(List.of(entry(9001L, "企业商城", true)));
        when(tenantApi.validTenant(9001L)).thenReturn(CommonResult.success(true));
        var result = service.getConfig();
        assertNull(result.getPublicTenantId());
        assertEquals(List.of(9001L), result.getTenants().stream().map(AppMallTenantConfigRespVO.Tenant::getTenantId).toList());
    }

    @Test
    void legacyTenantCannotBeReusedAsPublicTenantEvenWhenExplicitlyWhitelisted() {
        properties.setEnabled(true).setPublicTenantId(1L).setEntries(List.of(entry(1L, "旧商城", true)));
        assertUnavailable();
        verifyNoInteractions(tenantApi);
    }

    @Test
    void publicTenantMustBeExplicitlyIncludedAndVisible() {
        properties.setEnabled(true).setPublicTenantId(9001L).setEntries(List.of(entry(9002L, "其他商城", true)));
        assertUnavailable();
        properties.setEntries(List.of(entry(9001L, "内部商城", false)));
        assertUnavailable();
        verifyNoInteractions(tenantApi);
    }

    @Test
    void duplicateIdsFailClosedBeforeCheckingAnyTenant() {
        properties.setEnabled(true).setPublicTenantId(9001L).setEntries(List.of(
                entry(9001L, "公共商城", true), entry(9001L, "重复配置", false)));
        assertUnavailable();
        verifyNoInteractions(tenantApi);
    }

    @Test
    void invalidPublicTenantOrUnavailableStatusServiceCannotExposeTheCatalog() {
        enablePublicEntry();
        when(tenantApi.validTenant(9001L)).thenReturn(CommonResult.success(false));
        assertUnavailable();
        when(tenantApi.validTenant(9001L)).thenThrow(new IllegalStateException("status service unavailable"));
        assertUnavailable();
    }

    @Test
    void expiredPublicTenantIsUnavailableWithoutExposingInternalTenantName() {
        enablePublicEntry();
        when(tenantApi.validTenant(9001L)).thenReturn(CommonResult.error(123, "内部租户名称已到期"));
        ServiceException error = assertThrows(ServiceException.class, service::getConfig);
        assertEquals(MALL_TENANT_UNAVAILABLE.getMsg(), error.getMessage());
    }

    @Test
    void registrationAvailabilityIsPublishedWithoutTenantSelectionCapability() {
        enablePublicEntry();
        properties.getEntries().getFirst().setRegistrationEnabled(true);
        when(tenantApi.validTenant(9001L)).thenReturn(CommonResult.success(true));
        AppMallTenantConfigRespVO result = service.getConfig();
        assertTrue(result.isEnabled());
        assertFalse(result.getCapabilities().isRegistrationSelection());
        assertTrue(result.getTenants().getFirst().isRegistrationEnabled());
        assertFalse(result.getCapabilities().isBrowseSwitch());
    }

    private void enablePublicEntry() {
        properties.setEnabled(true).setPublicTenantId(9001L).setEntries(List.of(entry(9001L, "公共商城", true)));
    }

    @Test
    void previewRequiresExplicitRolloutAndNeverEnablesAuthenticationOrShopping() {
        enablePublicEntry();
        when(tenantApi.validTenant(9001L)).thenReturn(CommonResult.success(true));
        assertFalse(service.getConfig().getCapabilities().isBrowsePreview());
        properties.setBrowsePreviewEnabled(true);
        var result = service.getConfig();
        assertTrue(result.getCapabilities().isBrowsePreview());
        assertNoCapabilities(result);
        assertFalse(result.getTenants().getFirst().isRegistrationEnabled());
    }

    @Test
    void previewCannotBypassDisabledDirectoryOrUnavailablePublicTenant() {
        properties.setBrowsePreviewEnabled(true);
        assertFalse(service.getConfig().getCapabilities().isBrowsePreview());
        enablePublicEntry();
        when(tenantApi.validTenant(9001L)).thenReturn(CommonResult.success(false));
        assertUnavailable();
    }

    private void assertUnavailable() {
        assertEquals(MALL_TENANT_UNAVAILABLE.getCode(),
                assertThrows(ServiceException.class, service::getConfig).getCode());
    }

    @Test
    void scopedEntryRequiresExplicitRolloutAndValidPublicDirectory() {
        assertFalse(service.getConfig().getCapabilities().isScopedEntry());
        properties.setScopedEntryEnabled(true);
        assertFalse(service.getConfig().getCapabilities().isScopedEntry());
        enablePublicEntry();
        when(tenantApi.validTenant(9001L)).thenReturn(CommonResult.success(true));
        assertTrue(service.getConfig().getCapabilities().isScopedEntry());
        assertFalse(service.getConfig().getCapabilities().isBrowseSwitch());
        assertFalse(service.getConfig().getCapabilities().isRegistrationSelection());
        when(tenantApi.validTenant(9001L)).thenReturn(CommonResult.success(false));
        assertUnavailable();
    }

    @Test
    void scopedEntryPublishesBrowseSwitchOnlyWhenMultipleVisibleTenantsExist() {
        properties.setEnabled(true).setScopedEntryEnabled(true).setPublicTenantId(9001L).setEntries(List.of(
                entry(9001L, "公共商城", true), entry(9002L, "科盾商城", true)));
        when(tenantApi.validTenant(9001L)).thenReturn(CommonResult.success(true));
        when(tenantApi.validTenant(9002L)).thenReturn(CommonResult.success(true));
        assertTrue(service.getConfig().getCapabilities().isBrowseSwitch());
    }

    private void assertNoCapabilities(AppMallTenantConfigRespVO result) {
        assertFalse(result.getCapabilities().isBrowseSwitch());
        assertFalse(result.getCapabilities().isRegistrationSelection());
    }

    private MallTenantProperties.Entry entry(Long id, String name, boolean visible) {
        return new MallTenantProperties.Entry().setTenantId(id).setName(name).setPublicVisible(visible);
    }

}
