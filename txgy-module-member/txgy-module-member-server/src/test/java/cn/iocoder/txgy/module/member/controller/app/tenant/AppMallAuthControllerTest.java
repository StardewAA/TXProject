package cn.iocoder.txgy.module.member.controller.app.tenant;

import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.member.controller.app.tenant.vo.AppMallAuthReqVO.*;
import cn.iocoder.txgy.module.member.service.tenant.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppMallAuthControllerTest {
    @Test void everyCredentialEndpointDisablesAccessLogBodies() {
        int count = 0;
        for (var method : AppMallAuthController.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(PostMapping.class)) continue;
            count++; var log = method.getAnnotation(ApiAccessLog.class); assertNotNull(log);
            assertFalse(log.requestEnable()); assertFalse(log.responseEnable());
        }
        assertEquals(5, count);
    }
    @Test void infrastructureFailureDoesNotReachRawBodyErrorAuditor() {
        var auth = mock(MallTenantAuthService.class);
        var controller = new AppMallAuthController(mock(MallSmsChallengeService.class), auth);
        when(auth.passwordLogin(any(), any())).thenThrow(new IllegalStateException("must-not-log-secret"));
        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.login(new PasswordLogin(), new MockHttpServletRequest()));
        assertNull(error.getCause()); assertFalse(error.getMessage().contains("must-not-log-secret"));
    }
    @Test void untrustedForwardedIpCannotChooseRateLimitBucket() {
        var sender = mock(MallSmsChallengeService.class); var controller = new AppMallAuthController(sender, mock(MallTenantAuthService.class));
        var body = new Send(); body.setMobile("13900009991"); body.setTenantId(9001L); body.setPurpose(Purpose.REGISTER);
        var request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.2"); request.addHeader("X-Forwarded-For", "1.2.3.4");
        controller.send(body, request); verify(sender).send(9001L, "13900009991", Purpose.REGISTER, "127.0.0.2");
    }
    @Test void agreementIdentityMobileAndSixDigitCodeAreRequired() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator(); Register body = new Register();
            body.setMobile("13900009991"); body.setChallengeId("a".repeat(32)); body.setCode("123456");
            body.setPassword("Test1234"); body.setNickname("isolated"); body.setMemberIdentity("supplier");
            assertTrue(validator.validate(body).stream().anyMatch(v -> v.getPropertyPath().toString().equals("agree")));
            body.setAgree(true); assertTrue(validator.validate(body).isEmpty());
            body.setCode("9999"); assertFalse(validator.validate(body).isEmpty());
            body.setCode("123456"); body.setMemberIdentity("admin"); assertFalse(validator.validate(body).isEmpty());
        }
    }
}
