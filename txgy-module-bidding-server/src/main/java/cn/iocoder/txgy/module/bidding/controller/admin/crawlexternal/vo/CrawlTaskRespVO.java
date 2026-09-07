package cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo;
// 规范依据：设计文档 v0.6 §三（task 状态机/worker/重试/死信）；《招投标模块开发规范》§3.1（VO 规约）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 采集任务 Response")
@Data
public class CrawlTaskRespVO {

    @Schema(description = "任务 ID")
    private Long id;
    @Schema(description = "所属批次 ID（无批次=0）")
    private Long jobId;
    @Schema(description = "源代码")
    private String sourceCode;
    @Schema(description = "待抓 URL")
    private String url;
    @Schema(description = "URL SHA-256")
    private String urlHash;
    @Schema(description = "通道适配器 code")
    private String channelCode;
    @Schema(description = "解析/策略 code")
    private String parserCode;
    @Schema(description = "优先级（小=高）")
    private Integer priority;
    @Schema(description = "状态：PENDING/RUNNING/SUCCESS/FAILED/SKIPPED")
    private String status;
    @Schema(description = "已尝试次数（重试耗尽=FAILED 死信）")
    private Integer attempt;
    @Schema(description = "下次重试时间")
    private LocalDateTime nextRetryTime;
    @Schema(description = "执行节点标识")
    private String workerId;
    @Schema(description = "开始时间")
    private LocalDateTime startedAt;
    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;
    @Schema(description = "结果/错误说明（含死信原因）")
    private String message;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
