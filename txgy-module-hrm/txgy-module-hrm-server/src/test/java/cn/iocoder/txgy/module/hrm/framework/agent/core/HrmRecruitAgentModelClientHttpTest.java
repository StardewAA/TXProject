package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.hrm.framework.agent.config.HrmRecruitAgentModelProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_CALL_FAILED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_QUOTA_EXHAUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型适配层的 HTTP 行为测试。
 *
 * <p>用本地模拟服务代替真实模型，验证成功解析与各类失败的错误映射，
 * 不产生任何外部调用。</p>
 */
public class HrmRecruitAgentModelClientHttpTest {

    private HttpServer server;
    private String baseUrl;
    /** 记录服务端收到的请求体，用于断言发送内容 */
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedAuth = new AtomicReference<>();

    private volatile int responseStatus = 200;
    private volatile String responseBody = "";

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private HrmRecruitAgentModelClientImpl buildClient() {
        HrmRecruitAgentModelProperties properties = new HrmRecruitAgentModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("test-key");
        properties.setModel("qwen-flash");
        properties.setTimeoutMs(5000);
        return new HrmRecruitAgentModelClientImpl(properties);
    }

    private HrmRecruitAgentModelRequest buildRequest() {
        return new HrmRecruitAgentModelRequest()
                .setScene("RESUME_ANALYZE")
                .setPromptVersion("resume-analyze-v1")
                .setSystemPrompt("系统提示词")
                .setUserPayload("{\"resume\":{\"skills\":\"管道保温\"}}");
    }

    @Test
    public void testChat_parsesContentOnSuccess() {
        responseStatus = 200;
        responseBody = """
                {"choices":[{"message":{"role":"assistant","content":"{\\"overallScore\\":80}"}}]}
                """;

        HrmRecruitAgentModelResponse response = buildClient().chat(buildRequest());

        assertEquals("{\"overallScore\":80}", response.getContent());
        assertEquals("qwen-flash", response.getModel());
        assertNotNull(response.getDurationMs());
        // 请求按 OpenAI 兼容协议组装
        assertTrue(receivedBody.get().contains("\"model\":\"qwen-flash\""));
        assertTrue(receivedBody.get().contains("系统提示词"));
        assertEquals("Bearer test-key", receivedAuth.get());
    }

    @Test
    public void testChat_mapsQuotaErrorToActionableCode() {
        // 复现真实遇到的场景：账号免费额度用尽
        responseStatus = 400;
        responseBody = "{\"error\":{\"code\":\"AllocationQuota.FreeTierOnly\","
                + "\"message\":\"Free quota exhausted. Please add funds or disable the free tier only mode.\"}}";

        ServiceException exception = assertThrows(ServiceException.class,
                () -> buildClient().chat(buildRequest()));
        assertEquals(AGENT_MODEL_QUOTA_EXHAUSTED.getCode(), exception.getCode());
    }

    @Test
    public void testChat_surfacesProviderMessageForOtherErrors() {
        responseStatus = 401;
        responseBody = "{\"error\":{\"code\":\"InvalidApiKey\",\"message\":\"Incorrect API key provided\"}}";

        ServiceException exception = assertThrows(ServiceException.class,
                () -> buildClient().chat(buildRequest()));
        assertEquals(AGENT_MODEL_CALL_FAILED.getCode(), exception.getCode());
        // 错误描述要透出来，否则管理员无法定位问题
        assertTrue(exception.getMessage().contains("Incorrect API key"));
    }

    @Test
    public void testChat_failsWhenResponseHasNoChoices() {
        responseStatus = 200;
        responseBody = "{\"choices\":[]}";
        ServiceException exception = assertThrows(ServiceException.class,
                () -> buildClient().chat(buildRequest()));
        assertEquals(AGENT_MODEL_CALL_FAILED.getCode(), exception.getCode());
    }

    @Test
    public void testChat_failsOnNonJsonResponse() {
        responseStatus = 200;
        responseBody = "<html>502 Bad Gateway</html>";
        ServiceException exception = assertThrows(ServiceException.class,
                () -> buildClient().chat(buildRequest()));
        assertEquals(AGENT_MODEL_CALL_FAILED.getCode(), exception.getCode());
    }
}
