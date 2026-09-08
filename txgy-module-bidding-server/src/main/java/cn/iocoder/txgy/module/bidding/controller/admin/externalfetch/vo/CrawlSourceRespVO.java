package cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo;
// 规范依据：设计文档 v0.6 §四/§六；《招投标模块开发规范》§3.1（VO 规约）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 采集源 Response")
@Data
public class CrawlSourceRespVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "源代码（唯一）")
    private String sourceCode;
    @Schema(description = "源名称")
    private String sourceName;
    @Schema(description = "站点域名/入口")
    private String baseUrl;
    @Schema(description = "额外放行域名（半角逗号分隔）")
    private String allowedHosts;
    @Schema(description = "入口/栏目 URL 列表（JSON 数组）")
    private String entryUrlsJson;
    @Schema(description = "下载通道适配器 code")
    private String channelCode;
    @Schema(description = "默认解析/策略码")
    private String parserCodeDefault;
    @Schema(description = "抓取间隔（秒）")
    private Integer fetchIntervalSeconds;
    @Schema(description = "是否允许 robots 抓取")
    private Boolean robotsOk;
    @Schema(description = "启用开关（=外部链接 /go 白名单成员）")
    private Boolean enabled;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
