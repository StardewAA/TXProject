package cn.iocoder.txgy.module.promotion.service.kefu.chat;

import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.promotion.controller.admin.kefu.KeFuChatController;
import cn.iocoder.txgy.module.promotion.controller.admin.kefu.KeFuScopeController;
import cn.iocoder.txgy.module.promotion.controller.app.kefu.AppKeFuChatController;
import cn.iocoder.txgy.module.promotion.framework.kefu.KeFuScopedExceptionAdvice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real controllers + method security + transactional services + SQL; only external identities are fakes.
 * The two MVC instances represent the framework's separate APP/admin path prefixes, not a bypass router.
 * This does not claim to test production token issuance or a real customer/agent account. */
class KeFuChatMvcFlowTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private KeFuChatServiceTest fixture;
    private AnnotationConfigApplicationContext context;
    private MockMvc app, admin;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityConfiguration {}

    public static class PermissionStub {
        public boolean hasPermission(String permission) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.isAuthenticated()
                    && auth.getAuthorities().stream().anyMatch(grant -> grant.getAuthority().equals(permission));
        }
    }

    @BeforeEach void setup() {
        fixture = new KeFuChatServiceTest();
        fixture.setup();
        context = new AnnotationConfigApplicationContext();
        context.register(SecurityConfiguration.class);
        context.registerBean("ss", PermissionStub.class);
        var capability = new KeFuChatCapability();
        org.springframework.test.util.ReflectionTestUtils.setField(capability, "enabled", true);
        context.registerBean(AppKeFuChatController.class, () -> new AppKeFuChatController(fixture.service, capability));
        context.registerBean(KeFuChatController.class, () -> new KeFuChatController(fixture.service, capability));
        context.registerBean(KeFuScopeController.class, () -> new KeFuScopeController(fixture.scopes));
        context.refresh();
        app = MockMvcBuilders.standaloneSetup(context.getBean(AppKeFuChatController.class))
                .setControllerAdvice(new KeFuScopedExceptionAdvice()).build();
        admin = MockMvcBuilders.standaloneSetup(context.getBean(KeFuChatController.class), context.getBean(KeFuScopeController.class))
                .setControllerAdvice(new KeFuScopedExceptionAdvice()).build();
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        if (context != null) context.close();
        if (fixture != null) fixture.cleanup();
    }

    private void login(long id, int type, long tenant, String... permissions) {
        TenantContextHolder.setTenantId(tenant);
        var user = new LoginUser().setId(id).setUserType(type).setTenantId(tenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }

    private JsonNode response(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        var result = mvc.perform(request).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
    private JsonNode success(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        var result = response(mvc, request);
        assertEquals(0, result.path("code").asInt(), result.toString());
        return result.path("data");
    }
    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(value));
    }
    private JsonNode open() throws Exception {
        return success(app, body(post("/promotion/kefu-chat/open"), Map.of("scope", "SHOP", "shopId", 101)));
    }
    private Map<String, Object> change(JsonNode thread) {
        return Map.of("sessionId", thread.path("session").path("id").asText(),
                "version", thread.path("session").path("version").asLong());
    }
    private Map<String, Object> text(JsonNode thread, String key, String value) {
        return Map.of("sessionId", thread.path("session").path("id").asText(),
                "version", thread.path("session").path("version").asLong(), "clientMessageId", key, "text", value);
    }

    @Test void publishedFaqHumanHandoffClaimReplyReadCloseThroughRealControllers() throws Exception {
        assertTrue(success(app, get("/promotion/kefu-chat/capability")).path("enabled").asBoolean());
        login(99, 2, 1, "promotion:kefu-faq:manage", "promotion:kefu-agent:manage");
        var draft = success(admin, body(put("/promotion/kefu-chat/faq"), Map.of("scope", "SHOP", "shopId", 101,
                "version", 0, "entries", java.util.List.of(Map.of("id", "returns", "question", "如何退货", "keywords",
                        java.util.List.of("退货"), "answer", "请在本人订单申请售后", "enabled", true)))));
        success(admin, body(post("/promotion/kefu-chat/faq/publish"), Map.of("scope", "SHOP", "shopId", 101,
                "version", draft.path("version").asLong(), "reason", "隔离验收确认发布")));
        success(admin, body(post("/promotion/kefu-scope/agent"), Map.of("scope", "SHOP", "shopId", 101,
                "actorType", 2, "actorId", 22, "enabled", true, "version", 0, "reason", "隔离测试授权")));

        login(11, 1, 1);
        var thread = open();
        var sent = success(app, body(post("/promotion/kefu-chat/send"), text(thread, "mvc-faq-0001", "请问如何退货")));
        assertEquals("FAQ", sent.path("messages").get(1).path("senderRole").asText());
        thread = success(app, body(post("/promotion/kefu-chat/human"), change(thread)));
        assertEquals("WAITING_HUMAN", thread.path("mode").asText());
        String session = thread.path("session").path("id").asText();

        login(22, 2, 1, "promotion:kefu-agent:receive");
        success(admin, body(post("/promotion/kefu-chat/agent/heartbeat"), Map.of("scope", "SHOP", "shopId", 101, "online", true)));
        assertEquals(1, success(admin, get("/promotion/kefu-chat/agent/workbench").param("scope", "SHOP").param("shopId", "101")).size());
        success(admin, body(post("/promotion/kefu-scope/claim"), change(thread)));
        thread = success(admin, get("/promotion/kefu-chat/agent/thread").param("sessionId", session));
        var reply = success(admin, body(post("/promotion/kefu-chat/agent/send"), text(thread, "mvc-agent-0001", "人工客服已接待")));
        assertEquals(1, reply.path("messages").size());
        long messageId = reply.path("messages").get(0).path("id").asLong();

        login(11, 1, 1);
        var messages = success(app, get("/promotion/kefu-chat/messages").param("sessionId", session));
        assertEquals(3, messages.path("items").size());
        var read = success(app, body(post("/promotion/kefu-chat/read"), Map.of("sessionId", session, "messageId", messageId)));
        assertEquals(0, read.path("unreadCount").asLong());
        var humanMessage = success(app, body(post("/promotion/kefu-chat/send"), text(thread, "mvc-buyer-0002", "退货再确认")));
        assertEquals(1, humanMessage.path("messages").size(), "Human handoff must not produce another FAQ answer");
        login(22, 2, 1, "promotion:kefu-agent:receive");
        success(admin, body(post("/promotion/kefu-scope/close"), change(thread)));
        assertEquals(4, fixture.count());
    }

    @Test void actualMethodSecurityAndIdentityRejectAnonymousWrongTypeAndMissingPermission() throws Exception {
        SecurityContextHolder.clearContext();
        assertNotEquals(0, response(app, body(post("/promotion/kefu-chat/open"), Map.of("scope", "SHOP", "shopId", 101))).path("code").asInt());
        login(11, 1, 1, "promotion:kefu-faq:manage");
        assertNotEquals(0, response(admin, get("/promotion/kefu-chat/faq").param("scope", "SHOP").param("shopId", "101")).path("code").asInt());
        login(99, 2, 1);
        assertEquals(403, response(admin, get("/promotion/kefu-chat/faq").param("scope", "SHOP").param("shopId", "101")).path("code").asInt());
        assertNotEquals(0, response(app, body(post("/promotion/kefu-chat/open"), Map.of("scope", "SHOP", "shopId", 101))).path("code").asInt());
        assertEquals(0, fixture.count());
        assertEquals(0, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM promotion_kefu_session", Integer.class));
    }

    @Test void httpRetryReturnsPersistedReplyAndChangedPayloadConflicts() throws Exception {
        login(11, 1, 1);
        var thread = open();
        var payload = text(thread, "mvc-retry-0001", "等待回复");
        var first = success(app, body(post("/promotion/kefu-chat/send"), payload));
        var retry = success(app, body(post("/promotion/kefu-chat/send"), payload));
        assertEquals(first.path("messages"), retry.path("messages"));
        assertNotEquals(0, response(app, body(post("/promotion/kefu-chat/send"), text(thread, "mvc-retry-0001", "修改正文"))).path("code").asInt());
        assertEquals(2, fixture.count());
    }

    @Test void httpOtherOwnerOtherTenantAndMalformedBodyCannotReadOrWrite() throws Exception {
        login(11, 1, 1); var thread = open(); String session = thread.path("session").path("id").asText();
        success(app, body(post("/promotion/kefu-chat/send"), text(thread, "mvc-private-01", "不向其他账号展示")));
        login(33, 1, 1);
        assertNotEquals(0, response(app, get("/promotion/kefu-chat/messages").param("sessionId", session)).path("code").asInt());
        login(11, 1, 2);
        assertNotEquals(0, response(app, get("/promotion/kefu-chat/messages").param("sessionId", session)).path("code").asInt());
        login(11, 1, 1);
        assertEquals(400, response(app, post("/promotion/kefu-chat/send").contentType(MediaType.APPLICATION_JSON).content("{broken")).path("code").asInt());
        assertNotEquals(0, response(app, body(post("/promotion/kefu-chat/send"), Map.of("sessionId", session))).path("code").asInt());
        assertEquals(2, fixture.count());
    }
}
