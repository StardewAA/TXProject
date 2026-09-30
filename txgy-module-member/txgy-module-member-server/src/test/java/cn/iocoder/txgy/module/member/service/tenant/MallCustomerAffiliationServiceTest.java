package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.dal.dataobject.tenant.MallCustomerAffiliationDO;
import cn.iocoder.txgy.module.member.dal.mysql.tenant.MallCustomerAffiliationMapper;
import cn.iocoder.txgy.module.member.framework.tenant.config.*;
import cn.iocoder.txgy.module.product.api.enterprise.ProductEnterpriseCustomerApi;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MallCustomerAffiliationServiceTest {
    private MallTenantProperties tenants;
    private MallTenantAuthProperties auth;
    private ProductEnterpriseCustomerApi api;
    private MallCustomerAffiliationMapper mapper;
    private MallCustomerAffiliationService service;
    @BeforeEach void setup() {
        tenants = new MallTenantProperties().setEnabled(true).setPublicTenantId(9001L).setPlatformEnterpriseId(101L);
        auth = new MallTenantAuthProperties().setEnabled(true);
        api = mock(ProductEnterpriseCustomerApi.class); mapper = mock(MallCustomerAffiliationMapper.class);
        service = new MallCustomerAffiliationService(tenants, auth, api, mapper);
        TenantContextHolder.setTenantId(9001L);
        when(api.getCustomerOwner(101L)).thenReturn(CommonResult.success(new ProductEnterpriseCustomerApi.Owner(101L, 9001L, "平台", true)));
        when(mapper.lockMember(9001L, 71L)).thenReturn(71L);
        when(mapper.insert(any(MallCustomerAffiliationDO.class))).thenReturn(1);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void disabledLegacyNeedsNeitherNewSchemaNorProductService() {
        auth.setEnabled(false); service.bindRegistration(71L);
        assertNull(service.validateRegistration(9001)); verifyNoInteractions(api, mapper);
    }
    @Test void explicitOtherTenantNeverBindsToPlatform() {
        TenantContextHolder.setTenantId(9002L); service.bindRegistration(71L);
        verifyNoInteractions(api, mapper);
    }
    @Test void directoryDisabledFailsClosedButMissingRetiredPublicTenantSkipsAffiliation() {
        tenants.setEnabled(false); assertThrows(ServiceException.class, () -> service.bindRegistration(71L));
        tenants.setEnabled(true).setPublicTenantId(null); assertDoesNotThrow(() -> service.bindRegistration(71L));
        verifyNoInteractions(api, mapper);
    }
    @Test void missingEnterpriseCannotFallBackToAnyExistingEnterprise() {
        tenants.setPlatformEnterpriseId(null); assertThrows(ServiceException.class, () -> service.validateRegistration(9001));
        tenants.setPlatformEnterpriseId(0L); assertThrows(ServiceException.class, () -> service.validateRegistration(9001));
        verifyNoInteractions(api, mapper);
    }
    @Test void lookupUsesTargetContextAndRestoresCallerEvenOnFailure() {
        TenantContextHolder.setTenantId(9002L); TenantContextHolder.setIgnore(true);
        when(api.getCustomerOwner(101L)).thenAnswer(i -> {
            assertEquals(9001L, TenantContextHolder.getTenantId()); assertFalse(TenantContextHolder.isIgnore());
            throw new IllegalStateException("transport private details");
        });
        assertThrows(ServiceException.class, () -> service.validateRegistration(9001));
        assertEquals(9002L, TenantContextHolder.getTenantId()); assertTrue(TenantContextHolder.isIgnore());
    }
    @Test void missingForeignUnapprovedAndMismatchedEnterpriseRejected() {
        for (var owner : new ProductEnterpriseCustomerApi.Owner[] {null,
                new ProductEnterpriseCustomerApi.Owner(101L,9002L,"foreign",true),
                new ProductEnterpriseCustomerApi.Owner(102L,9001L,"wrong id",true),
                new ProductEnterpriseCustomerApi.Owner(101L,9001L,"unapproved",false)}) {
            when(api.getCustomerOwner(101L)).thenReturn(CommonResult.success(owner));
            assertThrows(ServiceException.class, () -> service.bindRegistration(71L));
        }
        verifyNoInteractions(mapper);
    }
    @Test void missingTenantIgnoredTenantOrMemberRejected() {
        TenantContextHolder.clear(); assertThrows(ServiceException.class, () -> service.bindRegistration(71L));
        TenantContextHolder.setTenantId(9001L); TenantContextHolder.setIgnore(true);
        assertThrows(ServiceException.class, () -> service.bindRegistration(71L));
        TenantContextHolder.setIgnore(false); assertThrows(ServiceException.class, () -> service.bindRegistration(null));
        verifyNoInteractions(mapper);
    }
    @Test void missingDisabledDeletedOrForeignMemberCannotBind() {
        when(mapper.lockMember(9001L,71L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.bindRegistration(71L));
        verify(mapper, never()).insert(any(MallCustomerAffiliationDO.class));
    }
    @Test void relationshipContainsOnlyCustomerFieldsAndExplicitTenant() {
        service.bindRegistration(71L);
        verify(mapper).insert(argThat((MallCustomerAffiliationDO r) -> r.getTenantId()==9001L
                && r.getMemberId()==71L && r.getServiceEnterpriseId()==101L && "PUBLIC_REGISTRATION".equals(r.getSource())));
    }
    @Test void retryIsIdempotentButConfigChangeCannotReassignOldCustomer() {
        var row = new MallCustomerAffiliationDO().setMemberId(71L).setServiceEnterpriseId(101L).setSource("PUBLIC_REGISTRATION");
        row.setTenantId(9001L); when(mapper.lockAffiliation(9001L,71L)).thenReturn(row);
        service.bindRegistration(71L); verify(mapper, never()).insert(any(MallCustomerAffiliationDO.class));
        row.setServiceEnterpriseId(999L); assertThrows(ServiceException.class, () -> service.bindRegistration(71L));
        verify(mapper, never()).updateById(any(MallCustomerAffiliationDO.class));
    }
    @Test void failedInsertCannotReportSuccessfulBinding() {
        when(mapper.insert(any(MallCustomerAffiliationDO.class))).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.bindRegistration(71L));
    }
    @Test void enterpriseRpcClientIsExplicitlyRegisteredInMemberService() {
        var config = cn.iocoder.txgy.module.member.framework.rpc.config.RpcConfiguration.class
                .getAnnotation(org.springframework.cloud.openfeign.EnableFeignClients.class);
        assertTrue(java.util.Arrays.asList(config.clients()).contains(ProductEnterpriseCustomerApi.class));
    }
}
