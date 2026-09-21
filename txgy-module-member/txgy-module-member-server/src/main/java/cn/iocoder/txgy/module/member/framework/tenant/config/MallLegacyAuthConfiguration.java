package cn.iocoder.txgy.module.member.framework.tenant.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.member.enums.ErrorCodeConstants.MALL_AUTH_LEGACY_DISABLED;

/** Server-side gate, independent of client headers/terminal flags. No shared security configuration changes. */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class MallLegacyAuthConfiguration implements WebMvcConfigurer {
    private final MallTenantAuthProperties auth;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LegacyGuard(auth));
    }

    public static final class LegacyGuard implements HandlerInterceptor {
        private static final Set<String> MUTATIONS = Set.of("/member/user/reset-password", "/member/user/update-password",
                "/member/user/update-mobile", "/member/user/update-mobile-by-weixin");
        private final MallTenantAuthProperties auth;
        public LegacyGuard(MallTenantAuthProperties auth) { this.auth = auth; }
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (!auth.isEnabled()) return true;
            String path = request.getServletPath();
            if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
            // Also matches a customized app-api prefix; excludes admin and RPC controllers below.
            if (handler instanceof org.springframework.web.method.HandlerMethod method
                    && !method.getBeanType().getPackageName().contains(".controller.app.")) return true;
            int index = path.indexOf("/member/");
            if (index < 0) return true;
            String memberPath = path.substring(index);
            if ((memberPath.startsWith("/member/auth/") && !Set.of("/member/auth/logout", "/member/auth/refresh-token").contains(memberPath))
                    || memberPath.startsWith("/member/social-user/") || MUTATIONS.contains(memberPath)) {
                throw exception(MALL_AUTH_LEGACY_DISABLED);
            }
            return true;
        }
    }
}
