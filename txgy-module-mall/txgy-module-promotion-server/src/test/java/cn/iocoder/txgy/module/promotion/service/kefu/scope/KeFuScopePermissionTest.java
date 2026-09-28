package cn.iocoder.txgy.module.promotion.service.kefu.scope;

import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.promotion.controller.admin.kefu.KeFuScopeController;
import cn.iocoder.txgy.module.promotion.controller.app.kefu.AppKeFuScopeController;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;

import static cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring method-security proxies, no OTP, no live accounts, no backend-service mocking of permissions. */
class KeFuScopePermissionTest {
    private AnnotationConfigApplicationContext context;
    private KeFuScopeController admin;
    private KeFuScopeService service;
    private Permissions permissions;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean KeFuScopeService service() { return mock(KeFuScopeService.class); }
        @Bean KeFuScopeController admin(KeFuScopeService service) { return new KeFuScopeController(service); }
        @Bean AppKeFuScopeController app(KeFuScopeService service) { return new AppKeFuScopeController(service); }
        @Bean(name = "ss") Permissions permissions() { return new Permissions(); }
    }
    public static class Permissions {
        Set<String> allowed = new HashSet<>();
        public boolean hasPermission(String permission) { return allowed.contains(permission); }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        admin = context.getBean(KeFuScopeController.class);
        service = context.getBean(KeFuScopeService.class);
        permissions = context.getBean(Permissions.class);
        TenantContextHolder.setTenantId(1L);
        login(2);
    }
    @AfterEach void cleanup() {
        context.close();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }
    @Test void oldKefuAndSupplierPermissionsCannotManageOrReceive() {
        permissions.allowed.addAll(Set.of("promotion:kefu-conversation:query", "promotion:kefu-message:send", "product:spu:update"));
        assertThrows(AccessDeniedException.class, () -> admin.grant(request()));
        assertThrows(AccessDeniedException.class, () -> admin.agents("SHOP", 101L, 1, 20));
        assertThrows(AccessDeniedException.class, () -> admin.list("SHOP", 101L, "WAITING", 1, 20));
        assertThrows(AccessDeniedException.class, () -> admin.claim(new ChangeRequest("test", 0L)));
        assertThrows(AccessDeniedException.class, () -> admin.close(new ChangeRequest("test", 0L)));
        verifyNoInteractions(service);
    }
    @Test void grantPermissionDoesNotAutomaticallyGiveReceptionPermission() {
        permissions.allowed.add("promotion:kefu-agent:manage");
        admin.grant(request());
        verify(service).grant(new Actor(99, 2), request());
        assertThrows(AccessDeniedException.class, () -> admin.claim(new ChangeRequest("test", 0L)));
    }
    @Test void receptionPermissionCannotGrantPrivileges() {
        permissions.allowed.add("promotion:kefu-agent:receive");
        admin.claim(new ChangeRequest("test", 0L));
        verify(service).claim(new Actor(99, 2), "test", 0L);
        assertThrows(AccessDeniedException.class, () -> admin.grant(request()));
    }
    @Test void memberCannotUseAdminEvenIfPermissionServiceSaysYes() {
        permissions.allowed.add("promotion:kefu-agent:manage");
        login(1);
        assertThrows(cn.iocoder.txgy.framework.common.exception.ServiceException.class, () -> admin.grant(request()));
        verifyNoInteractions(service);
    }
    @Test void appUsesMemberIdentityFromTokenNotFromBusinessRequest() {
        login(1);
        context.getBean(AppKeFuScopeController.class).open(new OpenRequest("SHOP", 101L));
        verify(service).open(new Actor(99, 1), "SHOP", 101L);
    }
    @Test void promotionRegistersEveryRpcRequiredByScopeService() {
        var annotation = cn.iocoder.txgy.module.promotion.framework.rpc.config.RpcConfiguration.class
                .getAnnotation(org.springframework.cloud.openfeign.EnableFeignClients.class);
        var clients = Arrays.asList(annotation.clients());
        assertTrue(clients.contains(cn.iocoder.txgy.module.product.api.shop.ProductShopApi.class));
        assertTrue(clients.contains(cn.iocoder.txgy.module.member.api.user.MemberUserApi.class));
        assertTrue(clients.contains(cn.iocoder.txgy.module.system.api.user.AdminUserApi.class));
    }
    private GrantRequest request() { return new GrantRequest("SHOP", 101L, 1, 22L, true, 0L, "测试"); }
    private void login(int type) {
        var user = new LoginUser().setId(99L).setUserType(type).setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
