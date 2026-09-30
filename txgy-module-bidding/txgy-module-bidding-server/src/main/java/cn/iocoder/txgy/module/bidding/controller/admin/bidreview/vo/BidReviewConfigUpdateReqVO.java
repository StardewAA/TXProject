package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 配置修改入参。
 *
 * <p>设计遵循：<b>不提供 apiKey 写入</b>——密钥只能由环境变量注入（规范 §4.8 L4），
 * 页面只能看掩码；如需按租户配密钥，走 P1 的 bidding_bid_review_ai_config（加密列）。
 */
@Data
public class BidReviewConfigUpdateReqVO {

    private Boolean enabled;
    private String model;
    private Boolean jsonMode;
    private Double temperature;
    private Integer maxTokens;
    private Integer timeoutSeconds;
    private Integer retryTimes;
    private Integer concurrency;
    private Integer chunkSize;
    @Schema(description = "变更原因（配置影响线上判定，必须留痕）")
    private String changeReason;
}
