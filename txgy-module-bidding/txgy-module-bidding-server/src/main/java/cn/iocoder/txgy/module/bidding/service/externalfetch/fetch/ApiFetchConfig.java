package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;

// 规范依据：外部信息获取模块设计 v1.0 §四（API 源样板：公共服务平台 API 通行形态——REST+JSON+鉴权头+分页+字段映射）；
//           栏目级 apiConfig（entry_urls_json 栏目对象可选字段），fetch_type=API 时驱动 ApiFetchDriver 直取 JSON，零代码加源。

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API 源栏目访问配置（api_config_json 反序列化模型；生产端列表直取驱动）。
 *
 * <p>字段约定（对应公共服务平台 REST/JSON 通行形态）：
 * <pre>
 * {
 *   "method": "POST|GET",
 *   "url": "https://.../queryContentPage",           // 列表接口地址
 *   "headers": {"X-Client-Id":"..."},                // 静态头（鉴权 token/签名头，动态签名走 Channel 适配器扩展）
 *   "bodyTemplate": "{\"pageNo\":{page},\"pageSize\":{size},\"dto\":{...}}",  // POST 体；{page}/{size} 占位
 *   "pageParam": "pageNo",  "sizeParam": "pageSize",  // GET query 分页参数名（POST 用 bodyTemplate 占位）
 *   "pageSize": 10,
 *   "rowsPath": "res.rows",                            // 条目数组 JSON 点路径
 *   "totalPath": "res.total",                          // 可选总数（空数组即停亦可）
 *   "mapping": {
 *     "title": "title", "url": "url", "date": "publishDate",
 *     "category": "categoryName", "contentHtml": "text"   // 列表带全文时声明；不带全文则内容走后续抓详情/落任务
 *   },
 *   "urlPrefix": "https://qyzb.shccmg.com/cms/default/webfile"  // 相对 URL 绝对化前缀（无则原样）
 * }
 * </pre>
 */
@Data
public class ApiFetchConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** HTTP 方法：GET/POST（缺省 GET） */
    private String method;
    /** 列表接口 URL（必填） */
    private String url;
    /** 静态请求头（如 X-Client-Id/X-Timestamp；签名算法类动态头不支持，走专属 Channel 适配器） */
    private Map<String, String> headers = new LinkedHashMap<>();
    /** POST 请求体模板：{page}/{size} 占位符替换为当前页码/页大小；GET 忽略 */
    private String bodyTemplate;
    /** GET query 页码参数名（缺省 pageNo） */
    private String pageParam;
    /** GET query 页大小参数名（缺省 pageSize） */
    private String sizeParam;
    /** 每页条数（缺省 10） */
    private Integer pageSize;
    /** 条目数组 JSON 点路径（缺省 rows） */
    private String rowsPath;
    /** 总数 JSON 点路径（可选；不配则以空数组停页） */
    private String totalPath;
    /** 字段映射：对方字段名 -> 本模块字段（title/url/date/category/contentHtml）；null=取同名字段 */
    private Map<String, String> mapping = new LinkedHashMap<>();
    /** 相对 URL 绝对化前缀（如 https://host/webfile；相对 url 拼此前缀，绝对 url 原样） */
    private String urlPrefix;

    public String getMethodOrDefault() {
        return method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
    }

    public String getPageParamOrDefault() {
        return pageParam == null || pageParam.isBlank() ? "pageNo" : pageParam.trim();
    }

    public String getSizeParamOrDefault() {
        return sizeParam == null || sizeParam.isBlank() ? "pageSize" : sizeParam.trim();
    }

    public int getPageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    public String getRowsPathOrDefault() {
        return rowsPath == null || rowsPath.isBlank() ? "rows" : rowsPath.trim();
    }

    /** 字段映射取值：mapping 声明了用声明名，否则同名字段（无则 null） */
    public String fieldName(String field) {
        String mapped = mapping.get(field);
        return mapped == null || mapped.isBlank() ? field : mapped;
    }

    /** 解析配置 JSON；非法/缺 url 返回 null（上层跳过并告警） */
    public static ApiFetchConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, ApiFetchConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON 点路径取值（如 res.rows → 逐级下钻）；无则返回 missing node */
    public static JsonNode at(JsonNode root, String dottedPath) {
        JsonNode cur = root;
        if (dottedPath == null || dottedPath.isBlank()) {
            return root;
        }
        for (String part : dottedPath.split("\\.", -1)) {
            if (cur == null || cur.isMissingNode() || cur.isNull()) {
                return cur;
            }
            cur = cur.get(part);
        }
        return cur == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : cur;
    }
}