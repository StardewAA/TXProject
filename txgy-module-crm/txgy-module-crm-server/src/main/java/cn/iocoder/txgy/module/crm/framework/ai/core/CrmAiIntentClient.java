package cn.iocoder.txgy.module.crm.framework.ai.core;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.crm.framework.ai.config.CrmAiProperties;
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
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.CRM_AI_CLASSIFY_FAIL;

/**
 * CRM AI 意图识别客户端
 *
 * 把用户的自然语言问题交给大模型，让它输出结构化意图（查客户还是查待办等）与过滤条件，
 * 前端再据此调用真实的 CRM 接口取数。
 *
 * 密钥与提示词都放在后端，前端只调 {@code /crm/ai/classify-intent}。
 */
@Slf4j
@Component
public class CrmAiIntentClient {

    /**
     * 意图识别提示词。
     * 注意：JSON 结构示例里不要嵌套引号（会干扰模型解析格式），描述尽量用短句。
     */
    private static final String SYSTEM_PROMPT = """
            你是一个 CRM 智能助手，负责把用户的中文提问转换成结构化查询意图。
            只返回一个 JSON 对象，不要 markdown、不要多余文字，格式如下：
            {"intent": "customer", "name": null, "month": false, "summary": "一句话说明"}
            其中 intent 只能取以下值之一：
            customer=客户, pool=客户公海, clue=线索, business=商机, todo=待办,
            contact=联系人, contract=合同, receivable=回款, receivablePlan=回款计划, product=产品。
            字段规则：
            1. name：用户提到的具体名称原文；只是泛问列表（如列出所有客户）时为 null；
            2. month：用户是否要求本月或这个月，是则 true，否则 false；
            3. summary：一句中文，说明将要展示什么，必须与实际查询结果一致。
            判断规则：
            1. 问题里出现待办、要联系、待跟进、逾期等字眼时，intent 一律为 todo，即使同时出现客户二字；
            2. 涉及客户公海、公海客户时 intent 为 pool；
            3. 结合上下文能确定业务对象时，选择最贴切的一个值，不要轻易返回 unknown。
            """;

    private static final int MAX_QUERY_LENGTH = 500;

    @Resource
    private CrmAiProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 是否已正确配置（未配置时前端会退化到关键词匹配）
     */
    public boolean isAvailable() {
        return Boolean.TRUE.equals(properties.getEnabled())
                && StrUtil.isNotBlank(properties.getBaseUrl())
                && StrUtil.isNotBlank(properties.getApiKey());
    }

    /**
     * 调用大模型做意图识别，返回其输出的 JSON 原文
     *
     * @param query 用户问题
     * @return 模型输出的 JSON 字符串
     */
    public String classify(String query) {
        if (!isAvailable()) {
            throw exception(CRM_AI_CLASSIFY_FAIL, "AI 未配置，请检查 txgy.crm.ai 配置项");
        }
        String content = StrUtil.sub(query, 0, MAX_QUERY_LENGTH);
        String url = StrUtil.removeSuffix(properties.getBaseUrl(), "/") + "/chat/completions";
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", properties.getModel(),
                    "temperature", 0.2,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", content))));
        } catch (Exception e) {
            throw exception(CRM_AI_CLASSIFY_FAIL, "请求体构造失败");
        }

        if (log.isDebugEnabled()) {
            log.debug("[classify][调用大模型 url={} body={}]", url, body);
        }
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getTimeout()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[classify][调用大模型失败]", e);
            throw exception(CRM_AI_CLASSIFY_FAIL, "无法连接大模型服务");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("[classify][大模型返回异常状态码 {}，body={}]", response.statusCode(), response.body());
            throw exception(CRM_AI_CLASSIFY_FAIL, "大模型返回状态码 " + response.statusCode());
        }
        try {
            JsonNode node = objectMapper.readTree(response.body());
            String answer = node.path("choices").path(0).path("message").path("content").asText("");
            if (StrUtil.isBlank(answer)) {
                throw exception(CRM_AI_CLASSIFY_FAIL, "大模型返回内容为空");
            }
            return answer;
        } catch (Exception e) {
            if (e instanceof cn.iocoder.txgy.framework.common.exception.ServiceException) {
                throw (cn.iocoder.txgy.framework.common.exception.ServiceException) e;
            }
            log.warn("[classify][解析大模型返回失败]", e);
            throw exception(CRM_AI_CLASSIFY_FAIL, "大模型返回格式异常");
        }
    }

}
