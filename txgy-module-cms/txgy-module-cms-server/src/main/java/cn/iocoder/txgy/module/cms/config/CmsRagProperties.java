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
    private String apiKey;

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
