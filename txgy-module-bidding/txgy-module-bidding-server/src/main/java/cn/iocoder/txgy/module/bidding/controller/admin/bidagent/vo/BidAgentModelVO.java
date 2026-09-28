package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 云端模型配置 VO（全局一套 + 每智能体可覆盖模型名/温度/max-tokens） */
public class BidAgentModelVO {

    @Schema(description = "保存入参")
    @Data
    public static class SaveReqVO {
        private Long id;
        @NotBlank(message = "配置名称不能为空")
        private String name;
        private String provider;
        @NotBlank(message = "base-url 不能为空")
        private String baseUrl;
        @NotBlank(message = "模型名不能为空")
        private String model;
        @Schema(description = "API Key；留空=不修改，返回时脱敏")
        private String apiKey;
        private BigDecimal temperature;
        private Integer maxTokens;
        private Integer timeoutSeconds;
        private Integer maxRetries;
        private Integer enabled;
        private String remark;
    }

    @Schema(description = "返回出参")
    @Data
    public static class RespVO {
        private Long id;
        private String name;
        private String provider;
        private String baseUrl;
        private String model;
        @Schema(description = "API Key（脱敏，仅回显后 4 位）")
        private String apiKeyMask;
        @Schema(description = "是否已配置 API Key")
        private Boolean apiKeyConfigured;
        private BigDecimal temperature;
        private Integer maxTokens;
        private Integer timeoutSeconds;
        private Integer maxRetries;
        private Integer enabled;
        private String remark;
    }

    @Schema(description = "内联连通性自检入参（不落库；id 非空时用库里 key）")
    @Data
    public static class TestReqVO {
        private Long id;
        private String baseUrl;
        private String model;
        private String apiKey;
        private Integer timeoutSeconds;
    }

    @Schema(description = "连通性自检出参")
    @Data
    public static class TestRespVO {
        private Boolean ok;
        private String message;
        private List<String> availableModels;
    }
}
