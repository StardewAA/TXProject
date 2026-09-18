package cn.iocoder.txgy.module.member.controller.app.tenant;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.txgy.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.txgy.module.member.service.tenant.MallImageAuthService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AppMallImageAuthControllerTest {
    private MallImageAuthService service;
    private ApiErrorLogCommonApi errorAudit;
    private GlobalExceptionHandler global;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;
    private Logger root;
    private ListAppender<ILoggingEvent> events;
    private static final String SECRET = "DoNotLog_Password_Sentinel_123";

    @BeforeEach void setup() {
        service = mock(MallImageAuthService.class);
        errorAudit = mock(ApiErrorLogCommonApi.class);
        global = spy(new GlobalExceptionHandler("isolated-image-auth", errorAudit));
        validator = new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AppMallImageAuthController(service))
                .setValidator(validator).setControllerAdvice(global).build();
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        events = new ListAppender<>(); events.start(); root.addAppender(events);
    }

    @AfterEach void cleanup() {
        if (root != null && events != null) root.detachAppender(events);
        if (events != null) events.stop();
        if (validator != null) validator.close();
    }

    private void rejectedSafely(String endpoint, String body) throws Exception {
        var result = mvc.perform(post("/member/mall-auth/" + endpoint)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(response.contains("\"code\":400"), "Expected the local fixed error response");
        assertTrue(response.contains("认证请求参数不正确，请检查后重试"));
        assertFalse(response.contains(SECRET));
        verifyNoInteractions(service, errorAudit);
        verify(global, never()).defaultExceptionHandler(any(), any());
        verify(global, never()).methodArgumentNotValidExceptionExceptionHandler(any());
        verify(global, never()).methodArgumentTypeInvalidFormatExceptionHandler(any());
        for (ILoggingEvent event : events.list) {
            assertFalse(event.getFormattedMessage().contains(SECRET), "Credential appeared in an application log");
            if (event.getThrowableProxy() != null) {
                assertFalse(ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy()).contains(SECRET),
                        "Rejected credential appeared in a logged exception");
            }
        }
    }

    private static String registration(String password) {
        return "{\"tenantId\":9001,\"loginName\":\"buyer_1\",\"password\":\"" + password
                + "\",\"confirmPassword\":\"" + password + "\",\"captchaSession\":\"" + "a".repeat(32)
                + "\",\"imageChallengeId\":\"" + "b".repeat(32)
                + "\",\"imageCode\":\"ABC23\",\"memberIdentity\":\"buyer\",\"agree\":true}";
    }

    @Test void oversizedRegistrationPasswordDoesNotEnterGlobalLoggerOrEchoRejectedValue() throws Exception {
        rejectedSafely("register-image", registration(SECRET));
    }

    @Test void oversizedLoginPasswordDoesNotEnterGlobalLoggerOrEchoRejectedValue() throws Exception {
        rejectedSafely("account-login", "{\"account\":\"buyer_1\",\"password\":\"" + SECRET + "\"}");
    }

    @Test void brokenJsonWithPasswordDoesNotEnterRawBodyErrorAudit() throws Exception {
        rejectedSafely("account-login", "{\"account\":\"buyer_1\",\"password\":\"" + SECRET + "\",");
    }

    @Test void wrongJsonFieldTypeDoesNotEchoInvalidFormatValue() throws Exception {
        rejectedSafely("register-image", registration("Test123456").replace("\"tenantId\":9001", "\"tenantId\":\"" + SECRET + "\""));
    }

    @Test void missingRequiredFieldsAndBodyFailBeforeServiceInvocation() throws Exception {
        rejectedSafely("register-image", "{}");
        rejectedSafely("account-login", "{}");
        rejectedSafely("image-challenge", "{}");
        rejectedSafely("account-login", "");
    }

    @Test void malformedImageChallengeAlsoUsesLocalHandler() throws Exception {
        rejectedSafely("image-challenge", "{\"loginName\":\"buyer_1\",\"captchaSession\":\"" + SECRET + "\",\"purpose\":\"REGISTER\"}");
    }

    @Test void everyCredentialEndpointDisablesBodiesAndAllPreInvocationValidationFamiliesAreCovered() throws Exception {
        int endpoints = 0;
        for (var method : AppMallImageAuthController.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(PostMapping.class)) continue;
            endpoints++;
            var accessLog = method.getAnnotation(ApiAccessLog.class);
            assertNotNull(accessLog); assertFalse(accessLog.requestEnable()); assertFalse(accessLog.responseEnable());
        }
        assertEquals(3, endpoints);
        var mapping = AppMallImageAuthController.class.getMethod("invalidCredentialRequest").getAnnotation(ExceptionHandler.class);
        assertNotNull(mapping);
        assertTrue(List.of(mapping.value()).containsAll(List.of(BindException.class, HttpMessageNotReadableException.class,
                ConstraintViolationException.class, HandlerMethodValidationException.class)));
    }

    @Test void memberBaseConfigurationSuppressesPreValidationConsoleRequestBodies() {
        // The shared interceptor prints at INFO before validation and ignores @ApiAccessLog requestEnable.
        // Protect the packaged member configuration as well as the controller's separate error handlers.
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        var properties = yaml.getObject();
        assertNotNull(properties);
        assertEquals("WARN", properties.getProperty(
                "logging.level.cn.iocoder.txgy.framework.apilog.core.interceptor.ApiAccessLogInterceptor"));
    }
}
