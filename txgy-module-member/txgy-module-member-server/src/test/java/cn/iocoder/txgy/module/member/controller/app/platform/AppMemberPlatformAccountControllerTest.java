package cn.iocoder.txgy.module.member.controller.app.platform;

import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.member.service.platform.MemberPlatformAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppMemberPlatformAccountControllerTest {

    private final MemberPlatformAccountService service = mock(MemberPlatformAccountService.class);
    private final AppMemberPlatformAccountController controller = new AppMemberPlatformAccountController();

    AppMemberPlatformAccountControllerTest() {
        ReflectionTestUtils.setField(controller, "platformAccountService", service);
    }

    @Test
    void resolvesFromTrustedLoginUserInsteadOfRequestParameters() {
        authenticate(316L, UserTypeEnum.MEMBER.getValue(), 1L);
        var expected = new MemberPlatformAccountRespDTO().setPlatformAccountId(8L);
        when(service.resolveRequired(316L, 1L)).thenReturn(expected);

        assertEquals(8L, controller.getSession().getCheckedData().getPlatformAccountId());
        verify(service).resolveRequired(316L, 1L);
    }

    @Test
    void rejectsAnonymousAndAdmin() {
        assertThrows(ServiceException.class, controller::getSession);
        authenticate(1L, UserTypeEnum.ADMIN.getValue(), 1L);
        assertThrows(ServiceException.class, controller::getSession);
        verifyNoInteractions(service);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(Long id, Integer userType, Long tenantId) {
        LoginUser user = new LoginUser().setId(id).setUserType(userType).setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
