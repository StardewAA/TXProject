package cn.iocoder.txgy.module.cms.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 法规 RAG 参数。默认值来自《RAG 检索配置参数》。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "txgy.cms.rag")
public class CmsRagProperties {

    private boolean enabled = false;
    /** ES 原生 RRF 需要对应商业许可；关闭时使用应用层 RRF，召回仍全部来自 ES。 */
    private boolean nativeRrfEnabled = false;
    /** ai-module：调用本地 AI 微服务动态选模；dashscope：兼容旧的直连方式。 */
    @NotBlank
    private String provider = "ai-module";
    private String apiKey;

    /** 可选。为空时由 AI 模块按启用状态和排序实时选择默认模型。 */
    private Long chatModelId;
    private Long embeddingModelId;

    @NotBlank
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    @NotBlank
    private String embeddingModel = "text-embedding-v3";

    @NotBlank
    private String chatModel = "qwen-plus";

    @Min(1)
    private int dimensions = 768;

    @Min(1)
    @Max(10)
    private int embeddingBatchSize = 10;

    @Min(1)
    @Max(50)
    private int topK = 5;

    @Min(0)
    @Max(1)
    private double similarityThreshold = 0.5;

    @Min(1)
    private int requestTimeoutSeconds = 60;

    @Min(256)
    private int maxAnswerTokens = 1200;

}
