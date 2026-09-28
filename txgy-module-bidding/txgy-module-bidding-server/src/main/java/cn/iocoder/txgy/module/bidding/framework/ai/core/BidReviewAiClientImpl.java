package cn.iocoder.txgy.module.bidding.framework.ai.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewAiLogDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewAiLogMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.framework.ai.config.BidReviewAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 智慧标书审查 · 大模型直连实现（DeepSeek，OpenAI 兼容协议）。
 *
 * <p>方案依据：§2.4（端点/模型/JSON 模式）、§6.3（输出校验与兜底）、§6.4（工程要求）。
 * 实现范式参照 CRM {@code CrmAiIntentClient}（模块直连大模型的既有先例），差异：
 * ① 本实现带并发信号量、重试退避与调用流水（审查是高频长文调用，必须可计量、可告警）；
 * ② 密钥只读 {@code bidding.review.ai.api-key}（BIDDING_REVIEW_AI_API_KEY）。
 *
 * <p>失败口径（不许"看起来成功"）：
 * 未配置 → BID_REVIEW_AI_DISABLED；401/403 → BID_REVIEW_AI_KEY_INVALID（不重试）；
 * 连接异常/5xx/429 → 指数退避重试后 BID_REVIEW_AI_CALL_FAILED；
 * 返回为空或非 JSON → BID_REVIEW_AI_PARSE_FAILED。
 */
@Slf4j
@Component
public class BidReviewAiClientImpl implements BidReviewAiClient {

    /** 提示词里必须出现该关键字：DeepSeek JSON 模式的前置要求（方案 §2.4 约束①） */
    private static final String JSON_KEYWORD = "json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private BidReviewAiProperties properties;

    @Resource
    private BidReviewAiLogMapper aiLogMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 并发闸门（懒加载，避免 @ConfigurationProperties 注入时序问题） */
    private volatile Semaphore semaphore;

    @Override
    public boolean isAvailable() {
        return properties.isAvailable();
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public String maskedApiKey() {
        return properties.maskedApiKey();
    }

    @Override
    public String chatJson(Long taskId, String step, String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            // 未配置：不 fail-fast，调用点明确报错（服务本身能正常启动）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_AI_DISABLED);
        }
        String body = buildRequestBody(systemPrompt, userPrompt);
        String url = StrUtil.removeSuffix(properties.getBaseUrl().trim(), "/") + "/chat/completions";
        long start = System.currentTimeMillis();
        int retryTimes = properties.getRetryTimes() == null ? 2 : Math.max(0, properties.getRetryTimes());
        int lastStatus = 0;
        String lastError = "";
        for (int attempt = 0; attempt <= retryTimes; attempt++) {
            try {
                Semaphore gate = ensureSemaphore();
                if (!gate.tryAcquire(properties.getTimeoutSeconds() == null ? 60 : properties.getTimeoutSeconds(),
                        TimeUnit.SECONDS)) {
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_AI_CALL_FAILED, "并发排队超时");
                }
                HttpResponse<String> response;
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds() == null ? 60
                                    : properties.getTimeoutSeconds()))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + properties.getApiKey())
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                } finally {
                    gate.release();
                }
                lastStatus = response.statusCode();
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    // 鉴权类错误重试无意义：直接判失败并提示密钥失效
                    writeLog(taskId, step, systemPrompt, userPrompt, "", System.currentTimeMillis() - start,
                            "FAILED", "HTTP " + response.statusCode());
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_AI_KEY_INVALID);
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String content = extractContent(response.body());
                    if (StrUtil.isBlank(content)) {
                        writeLog(taskId, step, systemPrompt, userPrompt, "", System.currentTimeMillis() - start,
                                "FAILED", "empty content");
                        throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_AI_PARSE_FAILED);
                    }
                    String json = stripCodeFence(content);
                    writeLog(taskId, step, systemPrompt, userPrompt, json, System.currentTimeMillis() - start,
                            "SUCCESS", "");
                    return json;
                }
                lastError = "HTTP " + response.statusCode();
                // 4xx（除 401/403/429）属参数错误，重试无意义
                if (response.statusCode() >= 400 && response.statusCode() < 500 && response.statusCode() != 429) {
                    writeLog(taskId, step, systemPrompt, userPrompt, "", System.currentTimeMillis() - start,
                            "FAILED", lastError);
                    throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_AI_CALL_FAILED, lastError);
                }
            } catch (cn.iocoder.txgy.framework.common.exception.ServiceException e) {
                throw e;
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName();
                log.warn("[chatJson][调用大模型异常 step={} attempt={}]", step, attempt, e);
            }
            // 指数退避（0.5s / 1s / 2s ...）
            if (attempt < retryTimes) {
                try {
                    Thread.sleep(500L * (1L << attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        writeLog(taskId, step, systemPrompt, userPrompt, "", System.currentTimeMillis() - start,
                "FAILED", StrUtil.blankToDefault(lastError, "unknown"));
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_AI_CALL_FAILED,
                StrUtil.blankToDefault(lastError, "HTTP " + lastStatus));
    }

    /** 单次探测（管理端"密钥自检"用）：最小 prompt，返回 {"ok":true,...} 的 JSON。 */
    public String probe() {
        String system = "你是连通性自检助手。请只输出 json，例如 {\"ok\":true}";
        return chatJson(null, "PROBE", system,
                "请输出 json：{\"ok\":true,\"echo\":\"probe\"}");
    }

    private Semaphore ensureSemaphore() {
        if (semaphore == null) {
            synchronized (this) {
                if (semaphore == null) {
                    int permits = properties.getConcurrency() == null ? 4 : Math.max(1, properties.getConcurrency());
                    semaphore = new Semaphore(permits, true);
                }
            }
        }
        return semaphore;
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        if (properties.getTemperature() != null) {
            payload.put("temperature", properties.getTemperature());
        }
        if (properties.getMaxTokens() != null && properties.getMaxTokens() > 0) {
            payload.put("max_tokens", properties.getMaxTokens());
        }
        if (Boolean.TRUE.equals(properties.getJsonMode())) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        // DeepSeek JSON 模式要求提示词里出现 "json" 字样，这里兜底补一句，避免调用方漏写
        String system = Boolean.TRUE.equals(properties.getJsonMode()) && !StrUtil.containsIgnoreCase(systemPrompt, JSON_KEYWORD)
                ? systemPrompt + "\n只输出 json，不要输出其它内容。"
                : systemPrompt;
        payload.put("messages", java.util.List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", userPrompt)));
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_AI_CALL_FAILED, "请求体构造失败");
        }
    }

    /** 只取 choices[0].message.content；reasoning_content 一律忽略（reasoner 模型会返回它）。 */
    private String extractContent(String responseBody) throws Exception {
        JsonNode node = MAPPER.readTree(responseBody);
        return node.path("choices").path(0).path("message").path("content").asText("");
    }

    /** 剥离 markdown code fence（模型常把 JSON 包在 ```json 里）。 */
    private String stripCodeFence(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
        }
        return text.trim();
    }

    /** 写调用流水：prompt 只落摘要哈希，不留全文（标书含敏感内容）。 */
    private void writeLog(Long taskId, String step, String systemPrompt, String userPrompt, String response,
                          long costMs, String status, String errorMsg) {
        try {
            BidReviewAiLogDO logDO = new BidReviewAiLogDO();
            logDO.setTaskId(taskId);
            logDO.setStep(step);
            logDO.setModel(properties.getModel());
            logDO.setPromptDigest(DigestUtil.sha256Hex(StrUtil.nullToEmpty(systemPrompt) + "\n" + StrUtil.nullToEmpty(userPrompt)));
            logDO.setRequestChars((StrUtil.length(systemPrompt) + StrUtil.length(userPrompt)));
            logDO.setResponseChars(StrUtil.length(response));
            logDO.setCostMs(costMs);
            logDO.setStatus(status);
            logDO.setErrorMsg(StrUtil.sub(StrUtil.nullToEmpty(errorMsg), 0, 200));
            // 租户由 TenantDatabaseInterceptor 按上下文自动补齐，无需手工传 tenant_id
            aiLogMapper.insert(logDO);
        } catch (Exception e) {
            // 流水写失败不能影响主流程
            log.warn("[writeLog][AI 调用流水落库失败 taskId={}]", taskId, e);
        }
    }
}
