package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import lombok.Data;

/** AI 配置出参（<b>只回显掩码密钥</b>，不回显明文）。 */
@Data
public class BidReviewConfigRespVO {

    private Boolean enabled;
    private String baseUrl;
    private String model;
    private Boolean jsonMode;
    private Double temperature;
    private Integer maxTokens;
    private Integer timeoutSeconds;
    private Integer retryTimes;
    private Integer concurrency;
    private Integer chunkSize;
    /** 掩码密钥（如 sk-****abcd）；未配置时为空串 */
    private String apiKeyMasked;
    /** 是否已配置可用 */
    private Boolean available;
}
