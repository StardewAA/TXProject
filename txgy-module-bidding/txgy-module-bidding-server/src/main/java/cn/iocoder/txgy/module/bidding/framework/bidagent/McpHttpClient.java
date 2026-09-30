package cn.iocoder.txgy.module.bidding.framework.bidagent;

// [规范依据]《招投标模块开发规范》§4.2
// [业务目标] 管理页对 MCP 服务做「连通性自检 + tools/list 预览 + 单次试调」；执行侧（Node）用官方 SDK 挂载工具
// [为什么] 自检必须在 Java 侧可见可操作（专员自己就能排查），而 stdio 传输需要起子进程，出于安全考虑只做
//          http/sse 两类云端 MCP 的探测；stdio 类型明确提示"请在底座侧自检"

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP JSON-RPC over HTTP 客户端（streamable-http / sse）。
 *
 * <p>只实现管理面需要的三种调用：{@code initialize}、{@code tools/list}、{@code tools/call}。
 * 响应同时兼容 JSON 与 SSE 帧（{@code data: {...}}）。
 */
@Component
@Slf4j
public class McpHttpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** tools/list：返回 JSON 数组（每个元素 name/description/inputSchema） */
    public JsonNode listTools(String url, Map<String, String> headers, Integer timeoutSeconds) {
        initialize(url, headers, timeoutSeconds);
        JsonNode result = rpc(url, headers, timeoutSeconds, "tools/list", MAPPER.createObjectNode());
        return result.path("tools");
    }

    /** tools/call：单次试调 */
    public JsonNode callTool(String url, Map<String, String> headers, Integer timeoutSeconds,
                             String toolName, Map<String, Object> arguments) {
        initialize(url, headers, timeoutSeconds);
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", MAPPER.valueToTree(arguments == null ? new LinkedHashMap<>() : arguments));
        return rpc(url, headers, timeoutSeconds, "tools/call", params);
    }

    private void initialize(String url, Map<String, String> headers, Integer timeoutSeconds) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", MAPPER.createObjectNode());
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "txgy-bidding");
        clientInfo.put("version", "1.0.0");
        rpc(url, headers, timeoutSeconds, "initialize", params);
        // 通知（无 id）
        rpc(url, headers, timeoutSeconds, "notifications/initialized", null);
    }

    private JsonNode rpc(String url, Map<String, String> headers, Integer timeoutSeconds,
                         String method, JsonNode params) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        if (params != null) {
            body.put("id", System.nanoTime());
            body.put("method", method);
            body.set("params", params);
        } else {
            body.put("method", method);
        }
        int timeout = timeoutSeconds == null || timeoutSeconds <= 0 ? 60 : timeoutSeconds;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("Accept", "application/json, text/event-stream")
                    .header("MCP-Protocol-Version", PROTOCOL_VERSION);
            if (headers != null) {
                headers.forEach((key, value) -> {
                    if (StrUtil.isNotBlank(key) && value != null) {
                        builder.header(key, value);
                    }
                });
            }
            HttpRequest request = builder.POST(
                    HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("MCP HTTP " + response.statusCode() + "：" + StrUtil.maxLength(response.body(), 160));
            }
            JsonNode node = parse(response.body());
            if (node.hasNonNull("error")) {
                throw new IllegalStateException("MCP 返回错误：" + StrUtil.maxLength(node.path("error").toString(), 160));
            }
            return node.path("result");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP 调用被中断");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("MCP 调用失败：" + ex.getMessage());
        }
    }

    /** 兼容裸 JSON 与 SSE 帧（取最后一个 data: 行） */
    private JsonNode parse(String body) throws java.io.IOException {
        String text = body == null ? "" : body.trim();
        if (text.startsWith("{")) {
            return MAPPER.readTree(text);
        }
        String last = null;
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                last = trimmed.substring(5).trim();
            }
        }
        if (last == null) {
            throw new java.io.IOException("MCP 响应体无法识别：" + StrUtil.maxLength(text, 160));
        }
        return MAPPER.readTree(last);
    }
}
