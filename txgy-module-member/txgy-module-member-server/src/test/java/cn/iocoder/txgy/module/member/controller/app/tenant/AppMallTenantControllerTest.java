package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallTenantSessionRespVO;
import cn.iocoder.txgy.module.member.service.tenant.MallCustomerAffiliationService;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallCustomerAffiliationRespVO;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.AUTH_TENANT_MISMATCH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppMallTenantControllerTest {

    private final AppMallTenantController controller = new AppMallTenantController();

    @Test
    void affiliationOnlyUsesTrustedSessionMember() {
        var customers=mock(MallCustomerAffiliationService.class);
        ReflectionTestUtils.setField(controller,"customerAffiliationService",customers);
        authenticate(UserTypeEnum.MEMBER.getValue(),9001L); TenantContextHolder.setTenantId(9001L);
        when(customers.getForMember(81L)).thenReturn(AppMallCustomerAffiliationRespVO.of(AppMallCustomerAffiliationRespVO.State.NONE));
        assertEquals(AppMallCustomerAffiliationRespVO.State.NONE,controller.getCustomerAffiliation().getCheckedData().state());
        verify(customers).getForMember(81L); verifyNoMoreInteractions(customers);
    }

    @Test
    void affiliationRejectsAnonymousAdminForeignAndIgnoredTenantBeforeService() {
        var customers=mock(MallCustomerAffiliationService.class);
        ReflectionTestUtils.setField(controller,"customerAffiliationService",customers);
        assertThrows(ServiceException.class,controller::getCustomerAffiliation);
        authenticate(UserTypeEnum.ADMIN.getValue(),9001L); TenantContextHolder.setTenantId(9001L);
        assertThrows(ServiceException.class,controller::getCustomerAffiliation);
        authenticate(UserTypeEnum.MEMBER.getValue(),9002L); assertThrows(ServiceException.class,controller::getCustomerAffiliation);
        authenticate(UserTypeEnum.MEMBER.getValue(),9001L); TenantContextHolder.setIgnore(true);
        assertThrows(ServiceException.class,controller::getCustomerAffiliation); verifyNoInteractions(customers);
    }

    @Test
    void invalidNonpositiveMemberOrTenantRejected() {
        authenticate(UserTypeEnum.MEMBER.getValue(),0L); TenantContextHolder.setTenantId(0L);
        assertThrows(ServiceException.class,controller::getCustomerAffiliation);
        LoginUser user=new LoginUser().setId(0L).setUserType(UserTypeEnum.MEMBER.getValue()).setTenantId(9001L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
        TenantContextHolder.setTenantId(9001L); assertThrows(ServiceException.class,controller::getCustomerAffiliation);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void sessionReturnsOnlyAuthenticatedMemberAndTenant() {
        authenticate(UserTypeEnum.MEMBER.getValue(), 9001L);
        TenantContextHolder.setTenantId(9001L);
        AppMallTenantSessionRespVO result = controller.getSession().getCheckedData();
        assertEquals(9001L, result.getTenantId());
        assertEquals(81L, result.getUserId());
    }

    @Test
    void differentRequestTenantCannotClaimAnotherSessionTenant() {
        authenticate(UserTypeEnum.MEMBER.getValue(), 9001L);
        TenantContextHolder.setTenantId(9002L);
        assertEquals(AUTH_TENANT_MISMATCH.getCode(),
                assertThrows(ServiceException.class, controller::getSession).getCode());
    }

    @Test
    void missingRequestTenantDoesNotInventAnAccountTenant() {
        authenticate(UserTypeEnum.MEMBER.getValue(), 9001L);
        assertEquals(AUTH_TENANT_MISMATCH.getCode(),
                assertThrows(ServiceException.class, controller::getSession).getCode());
    }

    @Test
    void anonymousOrAdminSessionCannotBeReportedAsMember() {
        assertEquals(UNAUTHORIZED.getCode(),
                assertThrows(ServiceException.class, controller::getSession).getCode());
        authenticate(UserTypeEnum.ADMIN.getValue(), 9001L);
        TenantContextHolder.setTenantId(9001L);
        assertEquals(UNAUTHORIZED.getCode(),
                assertThrows(ServiceException.class, controller::getSession).getCode());
    }

    private void authenticate(Integer userType, Long tenantId) {
        LoginUser user = new LoginUser().setId(81L).setUserType(userType).setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

}
