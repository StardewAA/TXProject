package cn.iocoder.txgy.module.hrm.framework.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 招聘 Agent 模型配置。
 *
 * <p>默认不启用。未配置完整（地址、密钥、模型名任一为空）时，
 * 适配层会直接抛出「模型尚未配置」，不会向任何外部服务发送数据。</p>
 */
@ConfigurationProperties(prefix = "hrm.agent.model")
@Data
public class HrmRecruitAgentModelProperties {

    /** 是否启用模型调用，默认关闭 */
    private boolean enabled = false;

    /**
     * OpenAI 兼容的 chat completions 基础地址。
     *
     * <p>通义千问：https://dashscope.aliyuncs.com/compatible-mode/v1
     * DeepSeek：https://api.deepseek.com/v1</p>
     */
    private String baseUrl;

    /** 访问密钥。只从配置读取，不落库、不打日志 */
    private String apiKey;

    /** 模型名，例如 qwen-flash、deepseek-chat */
    private String model;

    /** 单次调用超时（毫秒） */
    private int timeoutMs = 60_000;

    /**
     * 单次调用最大输出长度。
     *
     * <p>注意：思考型模型（如 GLM-4.5 系列）的推理过程同样占用这个额度，
     * 额度不足会导致 JSON 被截断。默认值已按「推理 + 结构化输出」预留。</p>
     */
    private int maxTokens = 4096;

    /** 采样温度，结构化输出场景保持低温 */
    private double temperature = 0.2D;

    /**
     * 是否启用 JSON 输出模式（OpenAI 兼容的 response_format）。
     *
     * <p>本模块所有场景都要求模型返回 JSON。实测部分模型在长 JSON 输出时
     * 会出现缺冒号、引号不闭合等语法错误，开启 JSON 模式可显著降低这类失败。
     * 若所用平台不支持该参数，可通过 extraBodyJson 覆盖或把此项关闭。</p>
     */
    private boolean jsonMode = true;

    /**
     * 追加到请求体里的额外参数（JSON 字符串），用于按模型平台微调。
     *
     * <p>结构化抽取类任务不需要模型「思考」，关闭后可显著减少耗时、费用与输出截断风险。
     * 不同平台参数名不同，因此做成透传，例如：
     * <ul>
     *   <li>智谱 GLM：{"thinking":{"type":"disabled"}}</li>
     *   <li>通义千问：{"enable_thinking":false}</li>
     * </ul>
     * 留空则不追加任何参数。</p>
     */
    private String extraBodyJson;

    /** 是否已具备调用条件 */
    public boolean isReady() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }
}
