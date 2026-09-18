package cn.iocoder.txgy.module.promotion.framework.kefu;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.apilog.core.filter.ApiAccessLogFilter;
import cn.iocoder.txgy.framework.apilog.core.interceptor.ApiAccessLogInterceptor;
import cn.iocoder.txgy.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.iocoder.txgy.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.txgy.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import cn.iocoder.txgy.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.spring.SpringUtils;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.web.config.WebProperties;
import cn.iocoder.txgy.framework.web.core.filter.CacheRequestBodyFilter;
import cn.iocoder.txgy.framework.web.core.filter.CacheRequestBodyWrapper;
import cn.iocoder.txgy.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.txgy.framework.web.core.handler.GlobalResponseBodyHandler;
import cn.iocoder.txgy.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.txgy.module.promotion.controller.admin.kefu.KeFuChatController;
import cn.iocoder.txgy.module.promotion.controller.admin.kefu.KeFuScopeController;
import cn.iocoder.txgy.module.promotion.controller.app.kefu.AppKeFuChatController;
import cn.iocoder.txgy.module.promotion.controller.app.kefu.AppKeFuScopeController;
import cn.iocoder.txgy.module.promotion.service.kefu.chat.KeFuChatCapability;
import cn.iocoder.txgy.module.promotion.service.kefu.chat.KeFuChatService;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeModels.Actor;
import cn.iocoder.txgy.module.promotion.service.kefu.scope.KeFuScopeService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static cn.iocoder.txgy.module.promotion.service.kefu.chat.KeFuChatModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KeFuScopedPrivacyTest {
    static final String SECRET = "PRIVATE-CHAT-PHONE-FAQ-DO-NOT-LOG";
    static final String SEND = "/app-api/promotion/kefu-chat/send";
    final ListAppender<ILoggingEvent> capture = new ListAppender<>();
    final List<Logger> loggers = new ArrayList<>();
    final List<Level> levels = new ArrayList<>();
    final List<ApiAccessLogCreateReqDTO> accessLogs = new ArrayList<>();
    final KeFuScopedPrivacyFilter filter = new KeFuScopedPrivacyFilter();
    final KeFuChatService service = mock(KeFuChatService.class);
    final ApiErrorLogCommonApi errors = mock(ApiErrorLogCommonApi.class);
    final ApiAccessLogCommonApi accesses = mock(ApiAccessLogCommonApi.class);
    MockMvc mvc;
    MockedStatic<SpringUtils> spring;

    @RestController @RequestMapping("/app-api/promotion/kefu-chat")
    static class Buyer extends AppKeFuChatController {
        Buyer(KeFuChatService service) { super(service, new KeFuChatCapability()); }
    }
    @RestController @RequestMapping("/admin-api/promotion/kefu-chat")
    static class Admin extends KeFuChatController {
        Admin(KeFuChatService service) { super(service, new KeFuChatCapability()); }
    }
    @RestController
    static class Other {
        @PostMapping("/app-api/promotion/other") CommonResult<String> echo(@RequestBody Map<String,String> body) {
            if (body.containsKey("fail")) throw new IllegalStateException(SECRET);
            return CommonResult.success(body.get("text"));
        }
    }
    static class ErrorApiFactory implements FactoryBean<ApiErrorLogCommonApi> {
        private final ApiErrorLogCommonApi delegate;
        ErrorApiFactory(ApiErrorLogCommonApi delegate) { this.delegate = delegate; }
        @Override public ApiErrorLogCommonApi getObject() { return delegate; }
        @Override public Class<?> getObjectType() { return ApiErrorLogCommonApi.class; }
    }

    @BeforeEach void setup() {
        filter.install(); capture.start();
        for (Class<?> type : List.of(ApiAccessLogInterceptor.class, ApiAccessLogFilter.class,
                GlobalExceptionHandler.class, KeFuScopedExceptionAdvice.class, KeFuScopedErrorLogDecorator.class)) {
            Logger logger = (Logger) LoggerFactory.getLogger(type);
            loggers.add(logger); levels.add(logger.getLevel()); logger.setLevel(Level.DEBUG); logger.addAppender(capture);
        }
        spring = mockStatic(SpringUtils.class); spring.when(SpringUtils::isProd).thenReturn(false);
        WebProperties properties = new WebProperties(); new WebFrameworkUtils(properties);
        doAnswer(inv -> { accessLogs.add(inv.getArgument(0)); return null; }).when(accesses).createApiAccessLogAsync(any());
        var decorated = (ApiErrorLogCommonApi) new KeFuScopedErrorLogDecorator().postProcessAfterInitialization(errors, "errors");
        mvc = MockMvcBuilders.standaloneSetup(new Buyer(service), new Admin(service), new Other())
                .setControllerAdvice(new KeFuScopedExceptionAdvice(), new GlobalResponseBodyHandler(),
                        new GlobalExceptionHandler("promotion-fixture", decorated))
                .addInterceptors(new ApiAccessLogInterceptor())
                .addFilters(filter, new CacheRequestBodyFilter(), new ApiAccessLogFilter(properties, "promotion-fixture", accesses))
                .build();
        login(1);
    }
    void login(int type) {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser().setId(99L).setUserType(type).setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
    }
    @AfterEach void cleanup() {
        filter.uninstall(); spring.close();
        for (int i=0;i<loggers.size();i++) { loggers.get(i).detachAppender(capture); loggers.get(i).setLevel(levels.get(i)); }
        capture.stop(); TenantContextHolder.clear(); SecurityContextHolder.clearContext();
    }
    String body() { return "{\"sessionId\":\"s\",\"version\":0,\"clientMessageId\":\"retry_0001\",\"text\":\""+SECRET+"\"}"; }
    void assertPrivate() {
        for (var event : capture.list) {
            assertFalse(event.getFormattedMessage().contains(SECRET), event.getFormattedMessage());
            if (event.getThrowableProxy()!=null) assertFalse(String.valueOf(event.getThrowableProxy().getMessage()).contains(SECRET));
        }
        for (var access : accessLogs) {
            assertNull(access.getRequestParams()); assertNull(access.getResponseBody());
            assertFalse(String.valueOf(access.getResultMsg()).contains(SECRET));
        }
        assertFalse(KeFuScopedPrivacyFilter.active());
    }

    @Test void realBodyReachesServiceWhileDevAndAccessLogsHaveNoBody() throws Exception {
        mvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(jsonPath("$.code").value(0)).andExpect(header().string("Cache-Control","private, no-store"));
        verify(service).send(eq(new Actor(99,1)),argThat(request->SECRET.equals(request.text())),eq(false));
        assertFalse(accessLogs.isEmpty()); verifyNoInteractions(errors); assertPrivate();
    }
    @Test void fixedBusinessCodeDoesNotEchoPotentiallyInterpolatedMessage() throws Exception {
        when(service.send(any(),any(),anyBoolean())).thenThrow(new ServiceException(KeFuScopeService.CONFLICT.getCode(),SECRET));
        mvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(jsonPath("$.code").value(KeFuScopeService.CONFLICT.getCode()))
                .andExpect(jsonPath("$.msg").value(KeFuScopeService.CONFLICT.getMsg()))
                .andExpect(header().exists("X-Kefu-Trace-Id"));
        verifyNoInteractions(errors); assertPrivate();
    }
    @Test void unknownBusinessMessageIsNotPassedThrough() throws Exception {
        when(service.send(any(),any(),anyBoolean())).thenThrow(new ServiceException(987654321,SECRET));
        mvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(errors); assertPrivate();
    }
    @Test void malformedJsonNeverEchoesValueOrCreatesCommonErrorBody() throws Exception {
        mvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON).content("{\"version\":\""+SECRET+"\"}"))
                .andExpect(jsonPath("$.code").value(400)).andExpect(jsonPath("$.msg").value("请求参数不正确"));
        verifyNoInteractions(service,errors); assertPrivate();
    }
    @Test void validationFailureDoesNotEchoRejectedText() throws Exception {
        mvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON).content(body().replace("retry_0001",SECRET+"!")))
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service,errors); assertPrivate();
    }
    @Test void databaseAndRpcExceptionsKeepMetadataNotCauseOrRequest() throws Exception {
        when(service.send(any(),any(),anyBoolean())).thenThrow(new DataAccessResourceFailureException(SECRET,
                new IllegalStateException("RPC upstream "+SECRET)));
        mvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(jsonPath("$.code").value(500)).andExpect(header().exists("X-Kefu-Trace-Id"));
        assertTrue(capture.list.stream().anyMatch(e->e.getFormattedMessage().contains("DataAccessResourceFailureException")));
        verifyNoInteractions(errors); assertPrivate();
    }
    @Test void deniedRequestKeepsForbiddenCodeWithoutEchoingSecurityMessage() throws Exception {
        when(service.send(any(),any(),anyBoolean())).thenThrow(new AccessDeniedException(SECRET));
        mvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(jsonPath("$.code").value(403));
        assertPrivate();
    }
    @Test void adminFaqBodyAlsoStaysPrivateAndReachesService() throws Exception {
        login(2);
        String faq="{\"scope\":\"SHOP\",\"shopId\":101,\"version\":0,\"entries\":[{\"id\":\"q\",\"question\":\""+SECRET+"\",\"keywords\":[\"物流\"],\"answer\":\""+SECRET+"\",\"enabled\":true}]}";
        mvc.perform(put("/admin-api/promotion/kefu-chat/faq").contentType(MediaType.APPLICATION_JSON).content(faq))
                .andExpect(jsonPath("$.code").value(0));
        verify(service).saveFaq(eq(new Actor(99,2)),argThat(value->SECRET.equals(value.entries().getFirst().answer())));
        assertPrivate();
    }
    @Test void nextNonScopedRequestOnSameThreadStillLogsNormally() throws Exception {
        mvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(jsonPath("$.code").value(0));
        assertPrivate();
        mvc.perform(post("/app-api/promotion/other").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\""+SECRET+"\"}"))
                .andExpect(jsonPath("$.data").value(SECRET));
        assertTrue(capture.list.stream().anyMatch(e->e.getFormattedMessage().contains(SECRET)));
        assertTrue(accessLogs.stream().anyMatch(e->String.valueOf(e.getRequestParams()).contains(SECRET)));
    }
    @Test void nonScopedExceptionsStillReachOriginalCommonErrorApi() throws Exception {
        mvc.perform(post("/app-api/promotion/other").contentType(MediaType.APPLICATION_JSON).content("{\"fail\":\""+SECRET+"\"}"))
                .andExpect(jsonPath("$.code").value(500));
        verify(errors).createApiErrorLogAsync(argThat(dto->dto.getRequestParams().contains(SECRET)));
    }
    @Test void preAuthenticationMarkerIsLogOnlyAndDoesNotConsumeBody() throws Exception {
        var request = new MockHttpServletRequest("POST",SEND); request.setContentType("application/json");
        request.setContent(body().getBytes(StandardCharsets.UTF_8));
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{
            assertNull(req.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE));
            var marker=(org.springframework.web.method.HandlerMethod)req.getAttribute(ApiAccessLogInterceptor.ATTRIBUTE_HANDLER_METHOD);
            assertFalse(marker.getMethodAnnotation(ApiAccessLog.class).requestEnable());
            assertEquals(body(),new String(req.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
        });
        assertFalse(KeFuScopedPrivacyFilter.active());
    }
    @Test void filterFinallyClearsPrivacyContextAfterUnhandledFailure() {
        var request = new MockHttpServletRequest("POST",SEND);
        assertThrows(IllegalStateException.class,()->filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{throw new IllegalStateException(SECRET);}));
        assertFalse(KeFuScopedPrivacyFilter.active());
    }
    @Test void preMvcExceptionFallbackUsesDecoratedDtoAndNoRawThrowableLog() throws Exception {
        var request = new MockHttpServletRequest("POST",SEND);request.setServletPath(SEND);
        request.setContentType("application/json");request.setContent(body().getBytes(StandardCharsets.UTF_8));
        var cached = new CacheRequestBodyWrapper(request);
        var decorated = (ApiErrorLogCommonApi)new KeFuScopedErrorLogDecorator().postProcessAfterInitialization(errors,"errors");
        filter.doFilter(cached,new MockHttpServletResponse(),(req,res)->
                new GlobalExceptionHandler("promotion-fixture",decorated).defaultExceptionHandler((jakarta.servlet.http.HttpServletRequest)req,
                        new IllegalStateException(SECRET)));
        verify(errors).createApiErrorLogAsync(argThat(dto->"{}".equals(dto.getRequestParams())
                && !dto.getExceptionMessage().contains(SECRET) && !dto.getExceptionStackTrace().contains(SECRET)
                && !dto.getExceptionRootCauseMessage().contains(SECRET)));
        assertPrivate();
    }
    @Test void decoratorWorksAsRealBeanPostProcessorForFeignInterfaceWithoutCircularLookup() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(KeFuScopedErrorLogDecorator.class);
            context.registerBean("upstream",ApiErrorLogCommonApi.class,()->errors);
            context.refresh();
            var bean=context.getBean(ApiErrorLogCommonApi.class);
            var dto=new ApiErrorLogCreateReqDTO();dto.setRequestUrl(SEND);dto.setRequestParams(SECRET);
            dto.setExceptionMessage(SECRET);dto.setExceptionRootCauseMessage(SECRET);dto.setExceptionStackTrace(SECRET);
            bean.createApiErrorLogAsync(dto); // No request ThreadLocal: also protects asynchronous dispatch.
            assertEquals("{}",dto.getRequestParams());assertFalse(dto.getExceptionStackTrace().contains(SECRET));
            verify(errors).createApiErrorLogAsync(dto);
        }
    }
    @Test void exactScopeMatchingDoesNotChangeLegacyCustomerServiceOrOtherPromotionLogs() {
        for(String path:List.of("/app-api/promotion/kefu-message/send","/admin-api/promotion/kefu-conversation/page",
                "/app-api/promotion/kefu-chatty/send","/rpc-api/promotion/kefu-chat/send","/app-api/product/spu/get"))
            assertFalse(KeFuScopedPrivacyFilter.matches(path),path);
        for(String path:List.of(SEND,"/admin-api/promotion/kefu-chat/faq","/app-api/promotion/kefu-scope/claim","/admin-api/promotion/kefu-scope/agent"))
            assertTrue(KeFuScopedPrivacyFilter.matches(path),path);
    }
    @Test void factoryCreatedRealFeignProxyAndDefaultAsyncMethodKeepInterfaceAndSafeDto() {
        List<String> encoded = new ArrayList<>();
        ApiErrorLogCommonApi actualFeign = feign.Feign.builder().contract(new SpringMvcContract())
                .encoder((body,type,template) -> encoded.add(((ApiErrorLogCreateReqDTO)body).getRequestParams()))
                .client((request,options) -> feign.Response.builder().request(request).status(200).reason("OK")
                        .headers(Map.of()).body("{}",StandardCharsets.UTF_8).build())
                .decoder((response,type) -> CommonResult.success(true))
                .target(ApiErrorLogCommonApi.class,"http://fixture.invalid"); // Client above never opens a socket.
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(KeFuScopedErrorLogDecorator.class);
            context.registerBean("upstream",ErrorApiFactory.class,() -> new ErrorApiFactory(actualFeign));
            context.refresh();
            var wrapped = context.getBean(ApiErrorLogCommonApi.class);
            var privateDto = new ApiErrorLogCreateReqDTO(); privateDto.setRequestUrl(SEND);privateDto.setRequestParams(SECRET);
            wrapped.createApiErrorLogAsync(privateDto);
            var otherDto = new ApiErrorLogCreateReqDTO();otherDto.setRequestUrl("/app-api/promotion/other");otherDto.setRequestParams(SECRET);
            wrapped.createApiErrorLog(otherDto).checkError();
            assertEquals(List.of("{}",SECRET),encoded);
        }
    }
    @Test void allFourControllersSuppressCommonBodyCapture() {
        for(Class<?> type:List.of(AppKeFuChatController.class,KeFuChatController.class,AppKeFuScopeController.class,KeFuScopeController.class))
            for(var method:type.getDeclaredMethods()) {
                if(method.getAnnotation(GetMapping.class)==null&&method.getAnnotation(PostMapping.class)==null&&method.getAnnotation(PutMapping.class)==null)continue;
                var log=method.getAnnotation(ApiAccessLog.class);assertNotNull(log,method.toString());assertFalse(log.requestEnable());assertFalse(log.responseEnable());
            }
    }
}
