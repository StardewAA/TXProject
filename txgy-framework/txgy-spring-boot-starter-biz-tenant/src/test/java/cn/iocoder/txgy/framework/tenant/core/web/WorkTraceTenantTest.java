package cn.iocoder.txgy.framework.tenant.core.web;

import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.config.TenantProperties;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkTraceTenantTest {
    @Test void archiveTenantSurvivesVisitContextRestoration() {
        var permissions=mock(SecurityFrameworkService.class);
        when(permissions.hasAnyPermissions("system:tenant:visit")).thenReturn(true);
        var interceptor=new TenantVisitContextInterceptor(new TenantProperties(),permissions);
        var request=new MockHttpServletRequest(); request.addHeader("visit-tenant-id","22");
        var response=new MockHttpServletResponse();
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(10L).setUserType(2).setTenantId(11L),request);
        TenantContextHolder.setTenantId(11L);
        try {
            assertTrue(interceptor.preHandle(request,response,new Object()));
            assertEquals(22L,TenantContextHolder.getTenantId());
            interceptor.afterCompletion(request,response,new Object(),null);
            assertEquals(11L,TenantContextHolder.getTenantId());
            assertEquals(22L,request.getAttribute("work_trace_tenant_id"));
        } finally { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    }
}
