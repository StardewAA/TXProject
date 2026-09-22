package cn.iocoder.txgy.module.promotion.service.kefu.chat;

import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.promotion.controller.admin.kefu.KeFuChatController;
import cn.iocoder.txgy.module.promotion.controller.app.kefu.AppKeFuChatController;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.Actor;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static cn.iocoder.txgy.module.promotion.service.kefu.chat.KeFuChatModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeFuChatPermissionTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean KeFuChatService service(){return mock(KeFuChatService.class);}
        @Bean KeFuChatCapability capability(){return new KeFuChatCapability();}
        @Bean KeFuChatController admin(KeFuChatService service,KeFuChatCapability cap){return new KeFuChatController(service,cap);}
        @Bean AppKeFuChatController app(KeFuChatService service,KeFuChatCapability cap){return new AppKeFuChatController(service,cap);}
        @Bean(name="ss") Permissions permissions(){return new Permissions();}
    }
    public static class Permissions {Set<String> allowed=new HashSet<>();public boolean hasPermission(String permission){return allowed.contains(permission);}}
    AnnotationConfigApplicationContext context;KeFuChatService service;KeFuChatController admin;AppKeFuChatController app;Permissions permissions;
    @BeforeEach void setup(){context=new AnnotationConfigApplicationContext(Config.class);service=context.getBean(KeFuChatService.class);admin=context.getBean(KeFuChatController.class);app=context.getBean(AppKeFuChatController.class);permissions=context.getBean(Permissions.class);TenantContextHolder.setTenantId(1L);login(2,1L);}
    @AfterEach void cleanup(){context.close();TenantContextHolder.clear();SecurityContextHolder.clearContext();}
    void login(int type,long tenant){var user=new LoginUser().setId(99L).setUserType(type).setTenantId(tenant);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));}
    @Test void oldMessagingOrProductPermissionCannotReceiveOrPublish(){permissions.allowed.addAll(Set.of("promotion:kefu-message:send","product:spu:update"));assertThrows(AccessDeniedException.class,()->admin.thread("id"));assertThrows(AccessDeniedException.class,()->admin.faq("SHOP",101L));verifyNoInteractions(service);}
    @Test void receptionDoesNotGrantFaqPublication(){permissions.allowed.add("promotion:kefu-agent:receive");admin.thread("id");verify(service).thread(new Actor(99,2),"id",true);assertThrows(AccessDeniedException.class,()->admin.faq("SHOP",101L));}
    @Test void faqPermissionDoesNotGrantReception(){permissions.allowed.add("promotion:kefu-faq:manage");admin.faq("SHOP",101L);verify(service).faq(new Actor(99,2),"SHOP",101L);assertThrows(AccessDeniedException.class,()->admin.thread("id"));}
    @Test void memberCannotUseAdminRouteEvenWithPermission(){permissions.allowed.add("promotion:kefu-agent:receive");login(1,1L);assertThrows(ServiceException.class,()->admin.thread("id"));verifyNoInteractions(service);}
    @Test void adminCannotUseBuyerOrMemberAgentRoute(){assertThrows(ServiceException.class,()->app.thread("id"));assertThrows(ServiceException.class,()->app.agentThread("id"));verifyNoInteractions(service);}
    @Test void wrongTokenTenantRejectedBeforeService(){login(1,2L);assertThrows(ServiceException.class,()->app.thread("id"));verifyNoInteractions(service);}
    @Test void publicCapabilityUsesNoAccountOrBusinessData(){SecurityContextHolder.clearContext();TenantContextHolder.clear();assertFalse(app.capability().getCheckedData().enabled());assertFalse(admin.capability().getCheckedData().enabled());verifyNoInteractions(service);}
    @Test void allChatRoutesDisableBodyLogging(){for(Class<?> type:List.of(AppKeFuChatController.class,KeFuChatController.class))for(var method:type.getDeclaredMethods()){
        if(method.getAnnotation(GetMapping.class)==null&&method.getAnnotation(PostMapping.class)==null&&method.getAnnotation(PutMapping.class)==null)continue;
        var log=method.getAnnotation(ApiAccessLog.class);assertNotNull(log,method.getName());assertFalse(log.requestEnable());assertFalse(log.responseEnable());}}
    @Test void privateResponsesAreNotCacheable(){var response=new org.springframework.mock.web.MockHttpServletResponse();app.noStore(response);assertEquals("private, no-store",response.getHeader("Cache-Control"));assertEquals("no-cache",response.getHeader("Pragma"));}
}
