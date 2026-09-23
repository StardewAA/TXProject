package cn.iocoder.txgy.module.member.controller.app.auth;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.member.controller.app.auth.vo.AppAuthRegisterReqVO;
import cn.iocoder.txgy.module.member.service.auth.MemberAuthService;
import cn.iocoder.txgy.module.member.service.tenant.MallAccountPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AppAuthControllerLegacyRegistrationTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void fixedTenantRegistrationIsFailClosedBeforeCallingLegacyService() {
        MemberAuthService auth = mock(MemberAuthService.class);
        AppAuthController controller = new AppAuthController();
        ReflectionTestUtils.setField(controller, "authService", auth);
        ReflectionTestUtils.setField(controller, "platformTenantId", 1L);
        ReflectionTestUtils.setField(controller, "legacyRegistrationEnabled", false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.register(new AppAuthRegisterReqVO()
                        .setMobile("13900000000").setCode("1234").setPassword("Password123")
                        .setNickname("legacy").setAgree(true)));

        assertEquals(MallAccountPolicy.LEGACY_REGISTRATION_DISABLED.getCode(), error.getCode());
        verifyNoInteractions(auth);
    }
}
