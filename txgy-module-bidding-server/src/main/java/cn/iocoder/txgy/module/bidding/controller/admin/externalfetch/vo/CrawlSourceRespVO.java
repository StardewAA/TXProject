package cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo;
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
    @Schema(description = "获取方式：CRAWLER=爬虫(列表HTML)/API=接口直取(JSON)")
    private String fetchType;
    @Schema(description = "API 源配置 JSON（fetch_type=API 时；结构化字段与服务端组装结果同源）")
    private String apiConfigJson;
    @Schema(description = "API 接口地址（结构化视图；与 apiConfigJson 同源）")
    private String apiUrl;
    @Schema(description = "请求方式 GET/POST（结构化视图）")
    private String apiMethod;
    @Schema(description = "请求头 JSON（结构化视图）")
    private String apiHeadersJson;
    @Schema(description = "POST 请求体模板（结构化视图）")
    private String apiBodyTemplate;
    @Schema(description = "GET 分页页码参数名（结构化视图）")
    private String apiPageParam;
    @Schema(description = "GET 分页页大小参数名（结构化视图）")
    private String apiSizeParam;
    @Schema(description = "每页条数（结构化视图）")
    private Integer apiPageSize;
    @Schema(description = "列表数据 JSON 路径（结构化视图）")
    private String apiRowsPath;
    @Schema(description = "总数 JSON 路径（结构化视图）")
    private String apiTotalPath;
    @Schema(description = "字段映射 JSON（结构化视图）")
    private String apiMappingJson;
    @Schema(description = "相对链接前缀（结构化视图）")
    private String apiUrlPrefix;
    @Schema(description = "默认解析/策略码（API 源=字段映射/AI 策略码）")
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
