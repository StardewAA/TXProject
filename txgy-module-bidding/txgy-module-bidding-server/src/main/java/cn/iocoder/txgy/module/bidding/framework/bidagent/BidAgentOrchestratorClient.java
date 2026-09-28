package cn.iocoder.txgy.module.bidding.framework.bidagent;

// [规范依据]《招投标模块开发规范》§4.2
// [业务目标] Java 侧对执行底座（Node bid-agent-service）的唯一出口：建任务 / 查任务 / 中断 / 取产物
// [为什么] 本模块是任务与配置的权威源，底座只负责执行；调用失败必须显式报错（1_060_111_086），不静默

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_ORCHESTRATOR_UNAVAILABLE;

@Component
@Slf4j
public class BidAgentOrchestratorClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BidAgentProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public BidAgentOrchestratorClient(BidAgentProperties properties) {
        this.properties = properties;
    }

    public boolean available() {
        return properties.getOrchestrator() != null
                && StrUtil.isNotBlank(properties.getOrchestrator().getBaseUrl());
    }

    /** 建任务（taskType=generate 为"一键生成标书"全流程） */
    public JsonNode createTask(String tenantId, Long projectId, String taskType, Map<String, Object> payload) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("tenantId", tenantId);
        body.put("projectId", String.valueOf(projectId));
        body.put("taskType", taskType);
        body.set("payload", MAPPER.valueToTree(payload == null ? Map.of() : payload));
        return call("POST", "/agent/tasks", body);
    }

    public JsonNode getTask(String agentTaskId) {
        return call("GET", "/agent/tasks/" + URLEncoder.encode(agentTaskId, StandardCharsets.UTF_8), null);
    }

    public void interruptTask(String agentTaskId) {
        call("POST", "/agent/tasks/" + URLEncoder.encode(agentTaskId, StandardCharsets.UTF_8) + "/interrupt",
                MAPPER.createObjectNode());
    }

    /** 人工确认（HIL）：把待确认问题的答复回填底座，继续推进等待中的任务 */
    public JsonNode confirmTask(String agentTaskId, List<Map<String, String>> answers) {
        ObjectNode body = MAPPER.createObjectNode();
        body.set("answers", MAPPER.valueToTree(answers == null ? List.of() : answers));
        return call("POST", "/agent/tasks/" + URLEncoder.encode(agentTaskId, StandardCharsets.UTF_8) + "/confirm",
                body);
    }

    /** 取产物（docx 二进制）；taskId 用于一键生成按任务隔离的工作区定位（可空=项目共享工作区） */
    public byte[] downloadDocx(String tenantId, Long projectId, String taskId) {
        String path = "/bid-file/docx?tenantId=" + URLEncoder.encode(StrUtil.nullToEmpty(tenantId), StandardCharsets.UTF_8)
                + "&projectId=" + URLEncoder.encode(String.valueOf(projectId), StandardCharsets.UTF_8);
        if (StrUtil.isNotBlank(taskId)) {
            path += "&taskId=" + URLEncoder.encode(taskId, StandardCharsets.UTF_8);
        }
        return callBytes(path);
    }

    private JsonNode call(String method, String path, ObjectNode body) {
        String text = callText(method, path, body);
        try {
            return MAPPER.readTree(text);
        } catch (Exception ex) {
            throw exception(BID_AGENT_ORCHESTRATOR_UNAVAILABLE, "响应无法解析：" + StrUtil.maxLength(text, 120));
        }
    }

    private byte[] callBytes(String path) {
        try {
            HttpRequest request = buildRequest(path).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw exception(BID_AGENT_ORCHESTRATOR_UNAVAILABLE, "取产物 HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw exception(BID_AGENT_ORCHESTRATOR_UNAVAILABLE, "取产物被中断");
        } catch (java.io.IOException ex) {
            throw exception(BID_AGENT_ORCHESTRATOR_UNAVAILABLE, "取产物失败：" + ex.getMessage());
        }
    }

    private String callText(String method, String path, ObjectNode body) {
        if (!available()) {
            throw exception(BID_AGENT_ORCHESTRATOR_UNAVAILABLE,
                    "未配置 bidding.bid-agent.orchestrator.base-url（底座地址）");
        }
        try {
            HttpRequest.Builder builder = buildRequest(path);
            HttpRequest request = "POST".equals(method)
                    ? builder.POST(HttpRequest.BodyPublishers.ofString(
                            body == null ? "{}" : MAPPER.writeValueAsString(body), StandardCharsets.UTF_8)).build()
                    : builder.GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("[BidAgentOrchestratorClient][call] HTTP {} path={} body={}", response.statusCode(), path,
                        StrUtil.maxLength(response.body(), 300));
                throw exception(BID_AGENT_ORCHESTRATOR_UNAVAILABLE,
                        "HTTP " + response.statusCode() + "：" + StrUtil.maxLength(response.body(), 160));
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw exception(BID_AGENT_ORCHESTRATOR_UNAVAILABLE, "调用被中断");
        } catch (java.io.IOException ex) {
            throw exception(BID_AGENT_ORCHESTRATOR_UNAVAILABLE, "调用失败：" + ex.getMessage());
        }
    }

    private HttpRequest.Builder buildRequest(String path) {
        BidAgentProperties.Orchestrator config = properties.getOrchestrator();
        String uri = config.getBaseUrl().replaceAll("/+$", "") + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds() == null ? 30 : config.getTimeoutSeconds()))
                .header("Content-Type", "application/json;charset=UTF-8");
        if (StrUtil.isNotBlank(properties.getAgentKey())) {
            builder.header("X-Agent-Key", properties.getAgentKey().trim());
        }
        return builder;
    }
}
