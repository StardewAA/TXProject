package cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo;
// 规范依据：设计文档 v0.6 §四/§六；《招投标模块开发规范》§3.1（VO 规约）、红线 8/§九（http(s)+IP 校验）

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 采集源保存 Request（create=id 空 / update=id 非空）")
@Data
public class CrawlSourceSaveReqVO {

    @Schema(description = "主键（更新时必填；新建为空）")
    private Long id;

    @Schema(description = "源代码（唯一）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "源代码不能为空")
    private String sourceCode;

    @Schema(description = "源名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "源名称不能为空")
    private String sourceName;

    @Schema(description = "站点域名/入口（http/https）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "站点入口 URL 不能为空")
    private String baseUrl;

    @Schema(description = "额外放行域名（半角逗号分隔：附件 CDN/文件服务器溯源域名；空=仅 base_url 域）")
    private String allowedHosts;

    @Schema(description = "入口/栏目 URL 列表（JSON 数组，可空）")
    private String entryUrlsJson;

    @Schema(description = "下载通道适配器 code（当前 GENERIC；未知 code 拒绝保存）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "下载通道 code 不能为空")
    private String channelCode;

    @Schema(description = "获取方式：CRAWLER=爬虫(列表HTML)/API=接口直取(JSON)；默认 CRAWLER（服务层校验合法值）")
    private String fetchType;

    @Schema(description = "API 源配置 JSON（fetch_type=API 时必填：method/url/headers/params/page/rowsPath/fieldMapping/envelope）")
    private String apiConfigJson;

    @Schema(description = "默认解析/策略码（URL 未单独审批时用；API 源=字段映射产出/AI 抽取策略码）")
    private String parserCodeDefault;

    @Schema(description = "抓取间隔（秒，频控；默认 3600）")
    @NotNull(message = "抓取间隔不能为空")
    private Integer fetchIntervalSeconds;

    @Schema(description = "是否允许按 robots 抓取（默认 false）")
    private Boolean robotsOk;

    @Schema(description = "启用开关（默认 false；置 true 后进入 /go 白名单）")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;
    // ========== API 源结构化配置（fetchType=API 时前端逐字段填写；服务端组装为 api_config_json 存储） ==========

    @Schema(description = "API 接口地址（fetchType=API 必填，http/https；如 https://data.xxx.gov.cn/gateway/api/1/xx）")
    private String apiUrl;

    @Schema(description = "请求方式：GET/POST（默认 GET）")
    private String apiMethod;

    @Schema(description = "请求头 JSON（可选：鉴权 token 等，如 {\"X-Client-Id\":\"xxx\"}）")
    private String apiHeadersJson;

    @Schema(description = "POST 请求体模板（可选：{page}/{size}/{siteId}/{categoryId} 占位）")
    private String apiBodyTemplate;

    @Schema(description = "GET 分页页码参数名（默认 pageNo）")
    private String apiPageParam;

    @Schema(description = "GET 分页页大小参数名（默认 pageSize）")
    private String apiSizeParam;

    @Schema(description = "每页条数（默认 10）")
    private Integer apiPageSize;

    @Schema(description = "列表数据 JSON 路径（默认 rows；如 data.list / res.rows）")
    private String apiRowsPath;

    @Schema(description = "总数 JSON 路径（可选）")
    private String apiTotalPath;

    @Schema(description = "字段映射 JSON（可选：对方字段名→本模块 title/url/date/category/contentHtml；缺省同名）")
    private String apiMappingJson;

    @Schema(description = "相对链接前缀（可选：公告 URL 为相对路径时绝对化用）")
    private String apiUrlPrefix;


}
