package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallTenantConfigRespVO;
import cn.iocoder.txgy.module.member.framework.tenant.config.*;
import cn.iocoder.txgy.module.member.service.tenant.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_TENANT_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppMallRegistrationOptionsControllerTest {
    MallTenantAuthProperties auth;
    MallTenantProperties tenants;
    MallAuthPolicy policy;
    MallTenantCatalogService catalog;
    AppMallRegistrationOptionsController controller;
    @BeforeEach void setup() {
        auth=new MallTenantAuthProperties().setEnabled(true);
        tenants=new MallTenantProperties().setEnabled(true).setPublicTenantId(9001L);
        tenants.setEntries(List.of(entry(9001,true,true),entry(9002,true,true),entry(9003,true,false),entry(9004,false,true)));
        policy=mock(MallAuthPolicy.class); catalog=mock(MallTenantCatalogService.class);
        when(policy.resolve(9001L,true)).thenReturn(9001L);
        when(catalog.getConfig()).thenReturn(new AppMallTenantConfigRespVO().setPublicTenantId(9001L).setTenants(List.of(item(9001),item(9002),item(9003))));
        controller=new AppMallRegistrationOptionsController(auth,tenants,policy,catalog);
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    private MallTenantProperties.Entry entry(long id,boolean visible,boolean register) {
        return new MallTenantProperties.Entry().setTenantId(id).setName("商城"+id).setPublicVisible(visible).setRegistrationEnabled(register);
    }
    private AppMallTenantConfigRespVO.Tenant item(long id) {return new AppMallTenantConfigRespVO.Tenant().setTenantId(id).setName("商城"+id);}
    @Test void disabledAuthDoesNotQueryRpcOrExposeConfiguredDirectory() {
        auth.setEnabled(false); var result=controller.getOptions().getCheckedData();
        assertFalse(result.enabled());assertNull(result.publicTenantId());assertTrue(result.tenants().isEmpty());assertFalse(result.crossTenantReady());
        verifyNoInteractions(policy,catalog);
    }
    @Test void disabledDirectoryDoesNotQueryRpc() {
        tenants.setEnabled(false);assertFalse(controller.getOptions().getCheckedData().enabled());verifyNoInteractions(policy,catalog);
    }
    @Test void onlyPublicRegistrationEnabledAndAvailableEntriesAreReturned() {
        var result=controller.getOptions().getCheckedData();
        assertTrue(result.enabled());assertEquals(9001L,result.publicTenantId());assertFalse(result.crossTenantReady());
        assertEquals(List.of(9001L,9002L),result.tenants().stream().map(AppMallRegistrationOptionsController.Tenant::tenantId).toList());
        verify(policy).resolve(9001L,true);
    }
    @Test void inactiveTenantFilteredByDirectoryCannotReappearFromConfiguration() {
        when(catalog.getConfig()).thenReturn(new AppMallTenantConfigRespVO().setPublicTenantId(9001L).setTenants(List.of(item(9001))));
        assertEquals(1,controller.getOptions().getCheckedData().tenants().size());
    }
    @Test void missingPublicEnterpriseOrAuthConfigurationDoesNotFallBackToTenantOne() {
        when(policy.resolve(9001L,true)).thenThrow(exception(MALL_TENANT_UNAVAILABLE));
        assertThrows(ServiceException.class,controller::getOptions);verifyNoInteractions(catalog);
    }
    @Test void publicDirectoryValidationUsesPublicTenantAndRestoresEntryTenant() {
        TenantContextHolder.setTenantId(1L);
        when(policy.resolve(9001L,true)).thenAnswer(invocation -> {
            assertEquals(9001L,TenantContextHolder.getRequiredTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            return 9001L;
        });
        assertTrue(controller.getOptions().getCheckedData().enabled());
        assertEquals(1L,TenantContextHolder.getRequiredTenantId());
    }
    @Test void revokedPublicRegistrationOrChangedPublicTenantFailsClosed() {
        tenants.setEntries(List.of(entry(9001,true,false)));
        assertThrows(ServiceException.class,controller::getOptions);
        tenants.setEntries(List.of(entry(9001,true,true)));
        when(catalog.getConfig()).thenReturn(new AppMallTenantConfigRespVO().setPublicTenantId(9002L).setTenants(List.of(item(9001))));
        assertThrows(ServiceException.class,controller::getOptions);
    }
    @Test void recordProjectionContainsNoPrivateEnterpriseOrSystemConfiguration() {
        assertEquals(List.of("tenantId","name"),java.util.Arrays.stream(AppMallRegistrationOptionsController.Tenant.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }
}
