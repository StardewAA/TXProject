package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.dal.dataobject.tenant.MallCustomerAffiliationDO;
import cn.iocoder.txgy.module.member.dal.mysql.tenant.MallCustomerAffiliationMapper;
import cn.iocoder.txgy.module.member.framework.tenant.config.*;
import cn.iocoder.txgy.module.product.api.enterprise.ProductEnterpriseCustomerApi;
import org.junit.jupiter.api.*;
import static cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallCustomerAffiliationRespVO.State.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MallCustomerProfileTest {
    MallTenantProperties tenants;
    MallTenantAuthProperties auth;
    ProductEnterpriseCustomerApi api;
    MallCustomerAffiliationMapper mapper;
    MallCustomerAffiliationService service;
    MallCustomerAffiliationDO row;

    @BeforeEach void setup() {
        tenants = new MallTenantProperties().setEnabled(true).setPublicTenantId(9001L).setPlatformEnterpriseId(101L);
        auth = new MallTenantAuthProperties().setEnabled(true);
        api = mock(ProductEnterpriseCustomerApi.class); mapper = mock(MallCustomerAffiliationMapper.class);
        service = new MallCustomerAffiliationService(tenants, auth, api, mapper);
        TenantContextHolder.setTenantId(9001L);
        row = new MallCustomerAffiliationDO().setMemberId(81L).setServiceEnterpriseId(101L).setSource("PUBLIC_REGISTRATION");
        row.setTenantId(9001L); row.setDeleted(false);
        when(mapper.selectForMember(9001L,81L)).thenReturn(row);
        when(api.getCustomerOwner(101L)).thenReturn(CommonResult.success(new ProductEnterpriseCustomerApi.Owner(101L,9001L,"服务企业",true)));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void legacyDisabledDoesNotQueryTableOrRpc() {
        auth.setEnabled(false); assertEquals(DISABLED,service.getForMember(81L).state()); verifyNoInteractions(mapper,api);
    }
    @Test void disabledDirectoryDoesNotQueryTableOrRpc() {
        tenants.setEnabled(false); assertEquals(DISABLED,service.getForMember(81L).state()); verifyNoInteractions(mapper,api);
    }
    @Test void absentRelationNeverFallsBackToConfiguredEnterprise() {
        when(mapper.selectForMember(9001L,81L)).thenReturn(null);
        assertEquals(NONE,service.getForMember(81L).state()); verifyNoInteractions(api);
    }
    @Test void persistedOwnerWinsOverChangedOrRemovedConfiguration() {
        tenants.setPublicTenantId(9002L).setPlatformEnterpriseId(null);
        var result=service.getForMember(81L); assertEquals(ACTIVE,result.state()); assertEquals("服务企业",result.serviceEnterpriseName());
        verify(mapper).selectForMember(9001L,81L); verifyNoMoreInteractions(mapper);
        verify(api).getCustomerOwner(101L); verifyNoMoreInteractions(api);
    }
    @Test void deletedMissingForeignWrongIdAndUnapprovedOwnersDoNotExposeName() {
        for (var owner : new ProductEnterpriseCustomerApi.Owner[]{null,
                new ProductEnterpriseCustomerApi.Owner(101L,9002L,"secret",true),
                new ProductEnterpriseCustomerApi.Owner(102L,9001L,"wrong",true),
                new ProductEnterpriseCustomerApi.Owner(101L,9001L,"revoked",false)}) {
            when(api.getCustomerOwner(101L)).thenReturn(CommonResult.success(owner));
            var result=service.getForMember(81L); assertEquals(OWNER_UNAVAILABLE,result.state()); assertNull(result.serviceEnterpriseName());
        }
    }
    @Test void rpcOutageIsNotRevocationAndDoesNotExposeError() {
        when(api.getCustomerOwner(101L)).thenThrow(new IllegalStateException("private transport data"));
        var result=service.getForMember(81L); assertEquals(TEMPORARILY_UNAVAILABLE,result.state()); assertNull(result.serviceEnterpriseName());
    }
    @Test void nullOrErrorEnvelopesAreTemporaryFailures() {
        when(api.getCustomerOwner(101L)).thenReturn(null);
        assertEquals(TEMPORARILY_UNAVAILABLE,service.getForMember(81L).state());
        when(api.getCustomerOwner(101L)).thenReturn(CommonResult.error(500,"private"));
        assertEquals(TEMPORARILY_UNAVAILABLE,service.getForMember(81L).state());
    }
    @Test void emptyNameIsNotActive() {
        when(api.getCustomerOwner(101L)).thenReturn(CommonResult.success(new ProductEnterpriseCustomerApi.Owner(101L,9001L," ",true)));
        assertEquals(TEMPORARILY_UNAVAILABLE,service.getForMember(81L).state());
    }
    @Test void invalidContextIsRejectedEvenWhenFeatureIsDisabled() {
        auth.setEnabled(false);
        TenantContextHolder.clear(); assertThrows(ServiceException.class,()->service.getForMember(81L));
        TenantContextHolder.setTenantId(0L); assertThrows(ServiceException.class,()->service.getForMember(81L));
        TenantContextHolder.setTenantId(9001L); TenantContextHolder.setIgnore(true);
        assertThrows(ServiceException.class,()->service.getForMember(81L));
        TenantContextHolder.setIgnore(false); assertThrows(ServiceException.class,()->service.getForMember(null));
        assertThrows(ServiceException.class,()->service.getForMember(0L)); verifyNoInteractions(api,mapper);
    }
    @Test void corruptOrUnscopedRowsFailClosedBeforeRpc() {
        row.setTenantId(9002L); assertThrows(ServiceException.class,()->service.getForMember(81L)); row.setTenantId(9001L);
        row.setMemberId(82L); assertThrows(ServiceException.class,()->service.getForMember(81L)); row.setMemberId(81L);
        row.setDeleted(true); assertThrows(ServiceException.class,()->service.getForMember(81L)); row.setDeleted(false);
        row.setSource("unknown"); assertThrows(ServiceException.class,()->service.getForMember(81L)); row.setSource("PUBLIC_REGISTRATION");
        row.setServiceEnterpriseId(null); assertThrows(ServiceException.class,()->service.getForMember(81L));
        verifyNoInteractions(api);
    }
    @Test void databaseFailureIsNotReportedAsNoRelationship() {
        when(mapper.selectForMember(9001L,81L)).thenThrow(new IllegalStateException("db offline"));
        assertThrows(IllegalStateException.class,()->service.getForMember(81L)); verifyNoInteractions(api);
    }
}
