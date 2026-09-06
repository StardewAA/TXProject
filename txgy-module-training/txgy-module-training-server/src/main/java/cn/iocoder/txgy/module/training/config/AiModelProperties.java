package cn.iocoder.txgy.module.training.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 大模型配置（试题智能导入）
 *
 * <p>支持任意 OpenAI 兼容接口的大模型（DeepSeek、豆包/火山方舟、通义、月之暗面等），
 * 通过 application.yaml 的 training.ai.* 配置，未配置密钥时自动降级为本地规则解析。</p>
 *
 * @author 云培训学院课题小组
 */
@Data
@Component
@ConfigurationProperties(prefix = "training.ai")
public class AiModelProperties {

    /**
     * 是否启用 AI（关闭时直接使用本地规则解析）
     */
    private Boolean enabled = false;

    /**
     * 服务商标识：deepseek / doubao / custom，仅用于日志与提示展示
     */
    private String provider = "deepseek";

    /**
     * 模型服务地址（OpenAI 兼容），如 https://api.deepseek.com
     */
    private String baseUrl = "https://api.deepseek.com";

    /**
     * 接口路径（OpenAI 兼容的对话接口）
     */
    private String chatPath = "/chat/completions";

    /**
     * API 密钥
     */
    private String apiKey = "";

    /**
     * 模型名称，如 deepseek-chat / doubao-pro-32k
     */
    private String model = "deepseek-chat";

    /**
     * 超时时间（毫秒）
     */
    private Integer timeoutMs = 60_000;

    /**
     * 单次提交给模型的最大文本长度（超出截断，避免超 token）
     */
    private Integer maxTextLength = 12_000;

    /**
     * 单次最多提取的试题数量
     */
    private Integer maxQuestions = 30;

    /**
     * 是否可用（开启且配置了密钥与地址）
     */
    public boolean available() {
        return Boolean.TRUE.equals(enabled)
                && apiKey != null && !apiKey.isBlank()
                && baseUrl != null && !baseUrl.isBlank();
    }
}
