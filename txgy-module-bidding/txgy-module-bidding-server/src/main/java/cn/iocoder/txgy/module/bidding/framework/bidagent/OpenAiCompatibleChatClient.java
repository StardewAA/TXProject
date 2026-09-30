package cn.iocoder.txgy.module.bidding.framework.bidagent;

// [规范依据]《招投标模块开发规范》§4.2（外部调用封装放 framework/）
// [业务目标] 智能体管理自持云端模型：试跑与连通性自检都走这里，不再依赖平台 AI 模块（bidding.ai.*）
// [为什么] 统一 OpenAI 兼容协议（百炼/DeepSeek/智谱/SiliconFlow 等），换厂商只改配置不改代码；
//          失败必须显式报错（BID_AGENT_LLM_UNAVAILABLE），不做"空文本冒充成功"

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_LLM_UNAVAILABLE;

/**
 * OpenAI 兼容 Chat 客户端（{@code POST {base-url}/chat/completions}）。
 */
@Component
@Slf4j
public class OpenAiCompatibleChatClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 单轮对话：把 systemPrompt 与 userContent 合成一次请求，返回模型文本 */
    public String chat(LlmConfig config, String systemPrompt, String userContent) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", config.getModel());
        if (config.getTemperature() != null) {
            body.put("temperature", config.getTemperature().doubleValue());
        }
        if (config.getMaxTokens() != null && config.getMaxTokens() > 0) {
            body.put("max_tokens", config.getMaxTokens());
        }
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        if (StrUtil.isNotBlank(systemPrompt)) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", StrUtil.nullToEmpty(userContent));

        JsonNode node = call(config, body);
        String content = node.path("choices").path(0).path("message").path("content").asText(null);
        if (StrUtil.isBlank(content)) {
            throw exception(BID_AGENT_LLM_UNAVAILABLE, "模型返回为空");
        }
        return content;
    }

    /** 连通性自检：发一条最小请求，验证 base-url / key / model 是否可用 */
    public String testConnection(LlmConfig config) {
        String content = chat(config, "你是连通性探针，只回复 OK。", "请回复 OK");
        return StrUtil.maxLength(content.trim(), 60);
    }

    private JsonNode call(LlmConfig config, ObjectNode body) {
        validate(config);
        String uri = config.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        int timeout = config.getTimeoutSeconds() == null || config.getTimeoutSeconds() <= 0 ? 180 : config.getTimeoutSeconds();
        int maxRetries = config.getMaxRetries() == null ? 2 : Math.max(0, Math.min(config.getMaxRetries(), 5));
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                        .timeout(Duration.ofSeconds(timeout))
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .header("Authorization", "Bearer " + config.getApiKey().trim())
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    return MAPPER.readTree(response.body());
                }
                String detail = StrUtil.maxLength(response.body(), 200);
                if (isRetryableStatus(response.statusCode()) && attempt < maxRetries) {
                    log.warn("[OpenAiCompatibleChatClient][call] HTTP {}，准备第 {} 次重试 uri={}",
                            response.statusCode(), attempt + 1, uri);
                    sleepBeforeRetry(attempt);
                    continue;
                }
                throw exception(BID_AGENT_LLM_UNAVAILABLE,
                        "模型 HTTP " + response.statusCode() + "：" + detail);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw exception(BID_AGENT_LLM_UNAVAILABLE, "模型调用被中断");
            } catch (HttpTimeoutException ex) {
                if (attempt < maxRetries) {
                    log.warn("[OpenAiCompatibleChatClient][call] 请求超时，准备第 {} 次重试 uri={} timeout={}s",
                            attempt + 1, uri, timeout);
                    try {
                        sleepBeforeRetry(attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw exception(BID_AGENT_LLM_UNAVAILABLE, "模型调用被中断");
                    }
                    continue;
                }
                log.error("[OpenAiCompatibleChatClient][call] 请求超时 uri={} timeout={}s retries={}", uri, timeout, maxRetries);
                throw exception(BID_AGENT_LLM_UNAVAILABLE,
                        "模型请求超时（" + timeout + " 秒，已重试 " + maxRetries + " 次）");
            } catch (java.io.IOException ex) {
                if (attempt < maxRetries) {
                    log.warn("[OpenAiCompatibleChatClient][call] 调用失败，准备第 {} 次重试 uri={} msg={}",
                            attempt + 1, uri, ex.getMessage());
                    try {
                        sleepBeforeRetry(attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw exception(BID_AGENT_LLM_UNAVAILABLE, "模型调用被中断");
                    }
                    continue;
                }
                log.error("[OpenAiCompatibleChatClient][call] 调用失败 uri={} msg={}", uri, ex.getMessage());
                throw exception(BID_AGENT_LLM_UNAVAILABLE, "模型调用失败：" + ex.getMessage());
            }
        }
        throw exception(BID_AGENT_LLM_UNAVAILABLE, "模型调用失败：未知错误");
    }

    private static boolean isRetryableStatus(int status) {
        return status == 429 || status >= 500;
    }

    private static void sleepBeforeRetry(int attempt) throws InterruptedException {
        long delay = Math.min(4000L, 500L * (1L << Math.min(attempt, 3)));
        Thread.sleep(delay);
    }
    private void validate(LlmConfig config) {
        if (config == null || StrUtil.isBlank(config.getBaseUrl()) || StrUtil.isBlank(config.getModel())) {
            throw exception(BID_AGENT_LLM_UNAVAILABLE, "未配置云端模型的 base-url 与模型名（智能体管理 → 云端模型）");
        }
        if (StrUtil.isBlank(config.getApiKey())) {
            throw exception(BID_AGENT_LLM_UNAVAILABLE, "未配置云端模型的 API Key（智能体管理 → 云端模型）");
        }
    }
}
