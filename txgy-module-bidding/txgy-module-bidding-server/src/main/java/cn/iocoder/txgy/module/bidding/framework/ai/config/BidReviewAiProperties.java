package cn.iocoder.txgy.module.bidding.framework.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智慧标书审查 · AI 配置（本模块专属，不经 AI 模块）。
 *
 * <p>方案依据：《智慧标书审查-设计方案》V2.0 §2.4（直连 + 专属 Key）/ §6.4（工程要求）。
 * 规范依据：§4.8（密钥类 L4——不落库、不进日志、不回前端）。
 *
 * <p>为什么配置前缀、密钥变量都与别人不同：审查是高频长文调用，独立 Key 后可单独计量、
 * 单独限流、单独吊销，出问题不连累 CRM AI 助手与公告域 AI 抽取。
 * <b>任何代码都不得回退读取 {@code DASHSCOPE_API_KEY}</b>。
 *
 * <p>DeepSeek 三条硬约束（见 §2.4）：① JSON 模式要求提示词含 "json" 字样；
 * ② {@code deepseek-reasoner} 不支持 response_format/temperature，需 {@code jsonMode=false}；
 * ③ 无视觉模型，扫描件不做图片转写（走 OCR 属 P2）。
 */
@Component
@ConfigurationProperties(prefix = "bidding.review.ai")
@Data
public class BidReviewAiProperties {

    /** 是否启用 AI 审查；关闭后一键生成仍可跑（仅规则匹配），报告标注"AI 未启用" */
    private Boolean enabled = true;

    /** OpenAI 兼容端点，DeepSeek 默认 https://api.deepseek.com/v1 */
    private String baseUrl;

    /** 本模块专属密钥（环境变量 BIDDING_REVIEW_AI_API_KEY），不落库、不进日志 */
    private String apiKey;

    /** 模型名：deepseek-chat（默认，支持 JSON 输出）/ deepseek-reasoner（需关 JSON 模式） */
    private String model = "deepseek-chat";

    /** 是否携带 response_format={"type":"json_object"}（DeepSeek 的 chat 模型支持） */
    private Boolean jsonMode = true;

    /** 温度（reasoner 类模型忽略该参数） */
    private Double temperature = 0.2;

    /** 单次回复最大 token（留空则不传） */
    private Integer maxTokens;

    /** 单次调用超时（秒） */
    private Integer timeoutSeconds = 60;

    /** 失败重试次数（仅连接异常/5xx/429 重试；401/403/400 不重试） */
    private Integer retryTimes = 2;

    /** 并发上限（信号量；防止一键生成打满该 Key 的配额） */
    private Integer concurrency = 4;

    /** 单次内联正文上限（字符；DeepSeek 上下文与输出预算取保守值） */
    private Integer chunkSize = 30_000;

    /** 是否已正确配置（未配置时不在启动期 fail-fast，只在调用点报错） */
    public boolean isAvailable() {
        return Boolean.TRUE.equals(enabled)
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }

    /** 掩码密钥（配置接口只回显这个，不回显明文） */
    public String maskedApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
