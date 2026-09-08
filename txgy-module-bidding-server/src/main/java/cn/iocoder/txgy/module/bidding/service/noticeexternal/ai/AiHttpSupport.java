package cn.iocoder.txgy.module.bidding.service.noticeexternal.ai;
// 规范依据：设计文档 v0.6 §7（AI 解析能力由 AI 模块承载）；外部信息获取 v1.0 §三（平台管理员 AI + 可调温度）；
//           接入形态=AI 模块 chat 会话 REST（本地 ai-server mock-enable 免鉴权联调）

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * AI 模块 REST 调用公共骨架（enabled=true 时注入，供各类 AI 场景复用）。
 *
 * <p>所有 AI 场景（结构化抽取/正文兜底排版等）共用同一调用链与租户隔离语义：
 * <ol>
 *   <li>base-url：AI 模块对外 admin-api 根地址（本地 ai-server mock-enable 亦可通）；</li>
 *   <li>租户跟随：显式传 tenant-id=触发者租户（TenantContextHolder 优先），无上下文（定时/离线）时
 *       回退 bidding.ai.tenant-id 配置 —— 保证各租户的会话/消息落自己的库、用自己租户的渠道/key；</li>
 *   <li>鉴权：admin-token 非空时注入 Bearer（mock-enable 可空）；</li>
 *   <li>会话参数：bidding.ai.role-id（可选，平台管理员 AI 角色=模型/提示词）与 bidding.ai.temperature
 *       （可选，覆盖会话默认；温度影响输出确定性与 md 代码块包裹倾向，联调可多次调参）在 createConversation 下发。</li>
 * </ol>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "bidding.ai", name = "enabled", havingValue = "true")
public class AiHttpSupport {

    /** AI 服务 admin-api 根地址（如 http://127.0.0.1:48080/admin-api），enabled=true 时必配 */
    @Value("${bidding.ai.base-url:}")
    private String baseUrl;

    /** 服务调用 token（ai-server mock-enable 时留空） */
    @Value("${bidding.ai.admin-token:}")
    private String adminToken;

    /** AI 调用所属租户兜底（无租户上下文时，默认平台租户 1） */
    @Value("${bidding.ai.tenant-id:1}")
    private Long fallbackTenantId;

    /** 单次 AI 调用超时（秒） */
    @Value("${bidding.ai.timeout-seconds:60}")
    private int timeoutSeconds;

    /** 聊天角色 id（可选：指定平台管理员配置的 AI 角色=模型/提示词模板；空=ai-server 默认角色） */
    @Value("${bidding.ai.role-id:}")
    private Long roleId;

    /** 会话温度（可选：空=不覆盖，沿用角色/会话默认；温度与输出确定性/md 包裹倾向相关，可调参调试） */
    @Value("${bidding.ai.temperature:}")
    private Double temperature;

    /** 单条回复最大 token（可选；防长回复/失控输出） */
    @Value("${bidding.ai.max-tokens:}")
    private Integer maxTokens;

    /** 共享 ObjectMapper（AI 场景包复用） */
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 触发者租户优先（跟随调用方，保证各租户用自己的 AI 渠道/key），无上下文时用配置兜底 */
    public Long resolveTenantId() {
        Long current = TenantContextHolder.getTenantId();
        return current != null ? current : fallbackTenantId;
    }

    /** 建默认会话（会话/消息/渠道按租户隔离；每次调用新建便于场景间不串上下文）。
     *  可带 role-id（平台管理员 AI 角色）；创建后按 bidding.ai.temperature/max-tokens 覆盖会话参数。 */
    public String createConversation() throws IOException {
        String createBody = roleId == null ? "{}" : "{\"roleId\":" + roleId + "}";
        JsonNode node = callJson("POST", "/ai/chat/conversation/create-my", createBody);
        String conversationId = node.path("data").asText();
        applyConversationTuning(conversationId);
        return conversationId;
    }

    /** 创建后按配置覆盖 temperature/maxTokens（ai-server 会话级参数：PUT /ai/chat/conversation/update-my；失败仅告警不阻断） */
    private void applyConversationTuning(String conversationId) {
        if (temperature == null && maxTokens == null) {
            return;
        }
        try {
            StringBuilder fields = new StringBuilder();
            fields.append("\"id\":").append(conversationId);
            if (temperature != null) {
                fields.append(",\"temperature\":").append(temperature);
            }
            if (maxTokens != null) {
                fields.append(",\"maxTokens\":").append(maxTokens);
            }
            callJson("PUT", "/ai/chat/conversation/update-my", "{" + fields + "}");
        } catch (Exception e) {
            log.warn("[applyConversationTuning][conv={}] 会话参数下发失败（忽略，沿用默认）: {}", conversationId, e.getMessage());
        }
    }

    /** 发送一条消息（content 由调用方拼 system+user；返回 AI 文本应答） */
    public String sendMessage(String conversationId, String content) throws IOException {
        String body = "{\"conversationId\":" + conversationId + ",\"content\":"
                + MAPPER.writeValueAsString(content) + "}";
        JsonNode node = callJson("POST", "/ai/chat/message/send", body);
        JsonNode receive = node.path("data").path("receive");
        return receive.path("content").asText(null);
    }

    /** 通用调用：显式 tenant-id + 可选 Bearer；code!=0 视为调用失败 */
    public JsonNode callJson(String method, String path, String body) throws IOException {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("bidding.ai.base-url 未配置");
        }
        String uri = baseUrl.replaceAll("/+$", "") + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json;charset=UTF-8");
        Long tenantId = resolveTenantId();
        if (tenantId != null) {
            builder.header("tenant-id", String.valueOf(tenantId));
        }
        if (StringUtils.hasText(adminToken)) {
            builder.header("Authorization", "Bearer " + adminToken.trim());
        }
        HttpRequest request;
        if ("POST".equals(method)) {
            request = builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        } else if ("PUT".equals(method)) {
            request = builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        } else {
            request = builder.GET().build();
        }
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node = MAPPER.readTree(resp.body());
            if (node.path("code").asInt() != 0) {
                throw new IOException("AI 模块返回错误 code=" + node.path("code").asText()
                        + " msg=" + node.path("msg").asText(""));
            }
            return node;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("AI 调用被中断", e);
        }
    }

}