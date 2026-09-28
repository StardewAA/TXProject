package cn.iocoder.txgy.module.hrm.framework.agent.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.hrm.framework.agent.config.HrmRecruitAgentModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_CALL_FAILED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_NOT_CONFIGURED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_QUOTA_EXHAUSTED;

/**
 * OpenAI 兼容协议的模型适配实现。
 *
 * <p>通义千问、DeepSeek、智谱、豆包等均提供 OpenAI 兼容的 chat completions 接口，
 * 因此只需配置基础地址、密钥与模型名即可切换，无需改代码。</p>
 *
 * <p>安全约定：未配置时不发起任何网络请求；日志不输出密钥与请求负载。</p>
 */
@Slf4j
@Component
@EnableConfigurationProperties(HrmRecruitAgentModelProperties.class)
public class HrmRecruitAgentModelClientImpl implements HrmRecruitAgentModelClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final HrmRecruitAgentModelProperties properties;

    public HrmRecruitAgentModelClientImpl(HrmRecruitAgentModelProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isReady() {
        return properties.isReady();
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    @Override
    public HrmRecruitAgentModelResponse chat(HrmRecruitAgentModelRequest request) {
        if (!isReady()) {
            // 未配置时明确报错，绝不返回编造的分析结果
            throw exception(AGENT_MODEL_NOT_CONFIGURED);
        }
        long startedAt = System.currentTimeMillis();
        JSONObject body = new JSONObject()
                .set("model", properties.getModel())
                .set("temperature", properties.getTemperature())
                .set("max_tokens", properties.getMaxTokens())
                .set("messages", new JSONArray()
                        .set(new JSONObject().set("role", "system")
                                .set("content", StrUtil.nullToEmpty(request.getSystemPrompt())))
                        .set(new JSONObject().set("role", "user")
                                .set("content", buildUserContent(request))));
        // 结构化输出场景要求严格 JSON：实测不加该参数时模型偶发语法错误（缺冒号、引号不闭合）。
        // 调用方未指定时沿用全局配置；自由对话会显式传 false 关掉它。
        boolean jsonOutput = request.getJsonOutput() != null
                ? request.getJsonOutput()
                : properties.isJsonMode();
        if (jsonOutput) {
            body.set("response_format", new JSONObject().set("type", "json_object"));
        }
        // 按平台追加额外参数（例如关闭思考模式），配置非法时忽略并记录，不影响主流程
        JSONObject extraBody = parseExtraBody();
        if (extraBody != null) {
            extraBody.forEach(body::set);
        }
        int maxRetries = properties.normalizedMaxRetries();
        for (int attempt = 0; ; attempt++) {
            try (HttpResponse response = HttpRequest
                    .post(StrUtil.removeSuffix(properties.getBaseUrl(), "/") + CHAT_COMPLETIONS_PATH)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout(properties.getTimeoutMs())
                    .execute()) {
                if (!response.isOk()) {
                // 只解析错误码与错误描述，绝不记录请求负载，避免把简历内容写进日志
                String providerCode = extractErrorField(response.body(), "code");
                String providerMessage = extractErrorField(response.body(), "message");
                log.warn("[hrm][agent] 模型调用失败：status={}, providerCode={}, providerMessage={}",
                        response.getStatus(), providerCode, StrUtil.maxLength(providerMessage, 300));
                // 额度类错误单独给出可操作提示，避免和网络故障混在一起
                if (providerCode != null && providerCode.startsWith("AllocationQuota")) {
                    throw exception(AGENT_MODEL_QUOTA_EXHAUSTED);
                }
                if (isRetryableStatus(response.getStatus()) && attempt < maxRetries) {
                    sleepBeforeRetry(attempt);
                    continue;
                }
                if (StrUtil.isNotBlank(providerMessage)) {
                    throw new ServiceException(AGENT_MODEL_CALL_FAILED.getCode(),
                            "模型调用失败：" + StrUtil.maxLength(providerMessage, 200));
                }
                throw exception(AGENT_MODEL_CALL_FAILED);
            }
                return buildResponse(response.body(), System.currentTimeMillis() - startedAt);
            } catch (ServiceException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                if (attempt < maxRetries && isRetryableException(ex)) {
                    log.warn("[hrm][agent] 模型调用瞬态异常，准备重试：attempt={}, type={}", attempt + 1,
                            ex.getClass().getSimpleName());
                    sleepBeforeRetry(attempt);
                    continue;
                }
                log.warn("[hrm][agent] 模型调用异常：{}", ex.getClass().getSimpleName());
                throw exception(AGENT_MODEL_CALL_FAILED);
            }
        }
    }

    private boolean isRetryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private boolean isRetryableException(RuntimeException ex) {
        String name = ex.getClass().getName().toLowerCase();
        return name.contains("timeout") || name.contains("socket") || name.contains("connect")
                || name.contains("interrupted");
    }

    private void sleepBeforeRetry(int attempt) {
        long delay = Math.min((long) properties.normalizedRetryBackoffMs() << attempt,
                properties.normalizedRetryMaxBackoffMs());
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw exception(AGENT_MODEL_CALL_FAILED);
        }
    }

    /**
     * 用户消息的 content。
     *
     * <p>只有带图时才用数组形式（OpenAI 兼容的多模态写法）；不带图仍然发一段纯字符串 ——
     * 其余六个结构化场景都走后者，不能因为加了图片就改变它们的请求形状，
     * 那等于拿一个刚验证过的改动去赌六个没重测过的场景。</p>
     */
    private Object buildUserContent(HrmRecruitAgentModelRequest request) {
        String text = StrUtil.nullToEmpty(request.getUserPayload());
        // 只记形状不记内容：这条日志就是为了回答「图到底有没有发出去」，
        // 出发去的是数组还是字符串、URL 多长，一眼就能看出来，而正文里的对话内容不进日志
        log.info("[hrm][agent] 组装用户消息：content={}, 文本 {} 字, 图片 {} 字符",
                StrUtil.isBlank(request.getImageDataUrl()) ? "string" : "array",
                text.length(), StrUtil.length(request.getImageDataUrl()));
        if (StrUtil.isBlank(request.getImageDataUrl())) {
            return text;
        }
        return new JSONArray()
                .set(new JSONObject().set("type", "text").set("text", text))
                .set(new JSONObject().set("type", "image_url")
                        .set("image_url", new JSONObject().set("url", request.getImageDataUrl())));
    }

    /**
     * 解析额外请求参数。为空或格式非法时返回 null，不影响主流程。
     */
    private JSONObject parseExtraBody() {
        String extraBodyJson = properties.getExtraBodyJson();
        if (StrUtil.isBlank(extraBodyJson)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(extraBodyJson);
        } catch (RuntimeException ex) {
            log.warn("[hrm][agent] 额外请求参数不是合法 JSON，已忽略");
            return null;
        }
    }

    /**
     * 从错误响应中取出 code / message。
     *
     * <p>只读取错误字段，不返回响应体其它内容，避免把可能夹带的业务数据带进日志。</p>
     */
    private String extractErrorField(String responseBody, String field) {
        if (StrUtil.isBlank(responseBody)) {
            return null;
        }
        try {
            JSONObject error = JSONUtil.parseObj(responseBody).getJSONObject("error");
            return error == null ? null : error.getStr(field);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 解析成功响应，取出文本内容与调用量。
     *
     * <p>只解析一次响应体，避免重复 JSON 解析；usage 缺失时留空而不是补 0，
     * 以免把「提供方没返回」误记成「确实消耗了 0 个 token」。</p>
     */
    private HrmRecruitAgentModelResponse buildResponse(String responseBody, long durationMs) {
        if (StrUtil.isBlank(responseBody)) {
            throw exception(AGENT_MODEL_CALL_FAILED);
        }
        HrmRecruitAgentModelResponse result = new HrmRecruitAgentModelResponse()
                .setModel(properties.getModel())
                .setDurationMs(durationMs);
        try {
            JSONObject json = JSONUtil.parseObj(responseBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw exception(AGENT_MODEL_CALL_FAILED);
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            String content = message == null ? null : message.getStr("content");
            if (StrUtil.isBlank(content)) {
                throw exception(AGENT_MODEL_CALL_FAILED);
            }
            result.setContent(content);
            JSONObject usage = json.getJSONObject("usage");
            if (usage != null) {
                result.setPromptTokens(usage.getInt("prompt_tokens"))
                        .setCompletionTokens(usage.getInt("completion_tokens"))
                        .setTotalTokens(usage.getInt("total_tokens"));
            }
            return result;
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw exception(AGENT_MODEL_CALL_FAILED);
        }
    }

    /** 从 OpenAI 兼容响应中取出首个候选的文本内容 */
}
