package cn.iocoder.txgy.module.bidding.framework.bidagent;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** 云端大模型运行参数（Java 试跑与 Node 编排共用同一套语义） */
@Data
@AllArgsConstructor
public class LlmConfig {

    /** OpenAI 兼容 base-url（如 https://dashscope.aliyuncs.com/compatible-mode/v1） */
    private String baseUrl;

    /** 模型名 */
    private String model;

    /** API Key（内存传递，不落日志/会话文件） */
    private String apiKey;

    private BigDecimal temperature;

    private Integer maxTokens;

    private Integer timeoutSeconds;

    private Integer maxRetries;
}
