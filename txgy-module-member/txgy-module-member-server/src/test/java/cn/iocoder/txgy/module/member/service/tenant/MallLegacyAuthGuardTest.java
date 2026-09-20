package cn.iocoder.txgy.module.member.service.tenant;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.member.framework.tenant.config.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class MallLegacyAuthGuardTest {
    @ParameterizedTest
    @ValueSource(strings = {"auth/login", "auth/register", "auth/sms-login", "auth/send-sms-code", "auth/validate-sms-code",
            "auth/social-login", "auth/weixin-mini-app-login", "auth/social-auth-redirect", "user/reset-password",
            "user/update-password", "user/update-mobile", "user/update-mobile-by-weixin", "social-user/bind"})
    void activationBlocksEveryOldEntryRegardlessOfClientFlags(String path) {
        var properties = new MallTenantAuthProperties(); properties.setEnabled(true);
        var guard = new MallLegacyAuthConfiguration.LegacyGuard(properties);
        var request = new MockHttpServletRequest("POST", "/app-api/member/" + path);
        request.addHeader("tenant-id", "1"); request.addHeader("mall", "false"); request.addHeader("terminal", "10");
        assertThrows(ServiceException.class, () -> guard.preHandle(request, new MockHttpServletResponse(), new Object()));
        properties.setEnabled(false);
        assertTrue(guard.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
    @ParameterizedTest
    @ValueSource(strings = {"auth/logout", "auth/refresh-token", "user/get", "mall-tenant/config", "mall-auth/register", "mall-auth/send-code"})
    void readLogoutRefreshAndNewProtocolRemainRoutable(String path) {
        var properties = new MallTenantAuthProperties(); properties.setEnabled(true);
        assertTrue(new MallLegacyAuthConfiguration.LegacyGuard(properties).preHandle(
                new MockHttpServletRequest("POST", "/app-api/member/" + path), new MockHttpServletResponse(), new Object()));
    }
}
