package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallTenantConfigRespVO;
import cn.iocoder.txgy.module.member.framework.tenant.config.*;
import cn.iocoder.txgy.module.member.service.tenant.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppMallRegistrationOptionsControllerTest {
    MallTenantAuthProperties auth;
    MallTenantProperties tenants;
    MallTenantCatalogService catalog;
    AppMallRegistrationOptionsController controller;
    @BeforeEach void setup() {
        auth=new MallTenantAuthProperties().setEnabled(true);
        tenants=new MallTenantProperties().setEnabled(true).setPublicTenantId(9001L);
        tenants.setEntries(List.of(entry(9001,true,true),entry(9002,true,true),entry(9003,true,false),entry(9004,false,true)));
        catalog=mock(MallTenantCatalogService.class);
        when(catalog.getConfig()).thenReturn(new AppMallTenantConfigRespVO().setPublicTenantId(9001L).setTenants(List.of(item(9001),item(9002),item(9003))));
        controller=new AppMallRegistrationOptionsController(auth,tenants,catalog);
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    private MallTenantProperties.Entry entry(long id,boolean visible,boolean register) {
        return new MallTenantProperties.Entry().setTenantId(id).setName("商城"+id).setPublicVisible(visible).setRegistrationEnabled(register);
    }
    private AppMallTenantConfigRespVO.Tenant item(long id) {return new AppMallTenantConfigRespVO.Tenant().setTenantId(id).setName("商城"+id);}
    @Test void disabledAuthDoesNotQueryRpcOrExposeConfiguredDirectory() {
        auth.setEnabled(false); var result=controller.getOptions().getCheckedData();
        assertFalse(result.enabled());assertNull(result.publicTenantId());assertTrue(result.tenants().isEmpty());assertFalse(result.crossTenantReady());assertFalse(result.selectionRequired());
        verifyNoInteractions(catalog);
    }
    @Test void disabledDirectoryDoesNotQueryRpc() {
        tenants.setEnabled(false);assertFalse(controller.getOptions().getCheckedData().enabled());verifyNoInteractions(catalog);
    }
    @Test void registrationDirectoryReturnsUsableExplicitTargetsWithoutDefaultTenant() {
        var result=controller.getOptions().getCheckedData();
        assertTrue(result.enabled());assertNull(result.publicTenantId());assertFalse(result.crossTenantReady());assertTrue(result.selectionRequired());
        assertEquals(List.of(9001L,9002L),result.tenants().stream().map(AppMallRegistrationOptionsController.Tenant::tenantId).toList());
    }
    @Test void inactiveTenantFilteredByDirectoryCannotReappearFromConfiguration() {
        when(catalog.getConfig()).thenReturn(new AppMallTenantConfigRespVO().setPublicTenantId(9001L).setTenants(List.of(item(9001))));
        assertEquals(1,controller.getOptions().getCheckedData().tenants().size());
    }
    @Test void catalogReadDoesNotChangeEntryTenant() {
        TenantContextHolder.setTenantId(1L);
        assertTrue(controller.getOptions().getCheckedData().enabled());
        assertEquals(1L,TenantContextHolder.getRequiredTenantId());
    }
    @Test void noUsableRegistrationTenantFailsClosedButCatalogDefaultDoesNotControlRegistration() {
        tenants.setEntries(List.of(entry(9001,true,false),entry(9002,true,false)));
        assertThrows(ServiceException.class,controller::getOptions);
        tenants.setEntries(List.of(entry(9001,true,true),entry(9002,true,true)));
        when(catalog.getConfig()).thenReturn(new AppMallTenantConfigRespVO().setPublicTenantId(9002L).setTenants(List.of(item(9001))));
        var result=controller.getOptions().getCheckedData();
        assertNull(result.publicTenantId());assertEquals(List.of(9001L),result.tenants().stream().map(AppMallRegistrationOptionsController.Tenant::tenantId).toList());
    }
    @Test void retiredTenant3000IsNeverPublishedEvenIfMisconfigured() {
        tenants.setEntries(List.of(entry(3000,true,true),entry(9001,true,true)));
        when(catalog.getConfig()).thenReturn(new AppMallTenantConfigRespVO().setPublicTenantId(9001L).setTenants(List.of(item(3000),item(9001))));
        var result=controller.getOptions().getCheckedData();
        assertEquals(List.of(9001L),result.tenants().stream().map(AppMallRegistrationOptionsController.Tenant::tenantId).toList());
    }
    @Test void recordProjectionContainsNoPrivateEnterpriseOrSystemConfiguration() {
        assertEquals(List.of("tenantId","name"),java.util.Arrays.stream(AppMallRegistrationOptionsController.Tenant.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }
}
