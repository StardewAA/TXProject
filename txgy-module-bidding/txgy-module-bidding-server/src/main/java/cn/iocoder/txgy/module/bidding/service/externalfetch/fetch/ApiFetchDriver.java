package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;

// 规范依据：外部信息获取模块设计 v1.0 §四（API 源样板：配置驱动列表直取；公共服务平台 REST/JSON 通行形态——
//           鉴权头/分页/rowsPath/字段映射全配置化，新增 API 源零代码）；列表 JSON 每行自带全文（如 QYZB rows.text）时直采，
//           不带全文则仅产出条目（详情/正文由后续抓取链路承担）。

import cn.iocoder.txgy.module.bidding.service.externalfetch.parser.NoticeParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * API 源列表直取驱动：按栏目 api_config_json（{@link ApiFetchConfig}）分页拉取 JSON 列表，
 * 按 rowsPath/字段映射产出 {@link NoticeParseResult.ListEntry}（标题/URL/日期/栏目/全文）。
 *
 * <p>职责边界：只做"按配置取列表页 → 行 → 条目"；窗口判定/停页/频控/去重/落库由调用方（Runner）负责，
 * 保证与爬虫源同语义。GET 用 query 分页参数，POST 用 bodyTemplate {page}/{size} 占位。
 */
@Slf4j
@Component
public class ApiFetchDriver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private HttpFetchClient httpFetchClient;

    /**
     * 拉取列表第 pageNo 页并映射为条目（原始顺序保留）。
     *
     * @return 本页条目（空=无更多）；HTTP/解析失败抛 IOException 由调用方计失败
     */
    public List<NoticeParseResult.ListEntry> fetchPage(ApiFetchConfig config, int pageNo) throws IOException {
        return fetchPage(config, pageNo, java.util.Map.of());
    }

    /** 分页拉取；vars 为 bodyTemplate/query 的额外占位（如 {categoryId}/{siteId} 栏目变量），见 runner 调用 */
    public List<NoticeParseResult.ListEntry> fetchPage(ApiFetchConfig config, int pageNo, java.util.Map<String, String> vars) throws IOException {
        String url = config.getUrl();
        if (url == null || url.isBlank()) {
            throw new IOException("API 源配置缺 url");
        }
        String json;
        if ("POST".equals(config.getMethodOrDefault())) {
            String body = renderBody(config, pageNo, vars);
            json = httpFetchClient.postJsonWithHeaders(url, body, config.getHeaders());
        } else {
            String pageUrl = appendQuery(url, config.getPageParamOrDefault() + "=" + pageNo
                    + "&" + config.getSizeParamOrDefault() + "=" + config.getPageSizeOrDefault());
            json = httpFetchClient.getTextWithHeaders(pageUrl, config.getHeaders());
        }
        return mapEntries(config, json);
    }

    /** 解析响应 JSON → 条目列表（rowsPath 取数组；行对象按字段映射抽取；解析失败抛 IOException） */
    private List<NoticeParseResult.ListEntry> mapEntries(ApiFetchConfig config, String json) throws IOException {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IOException("API 响应非 JSON: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        JsonNode rows = ApiFetchConfig.at(root, config.getRowsPathOrDefault());
        if (rows == null || !rows.isArray()) {
            throw new IOException("API 响应 rowsPath 非数组: " + config.getRowsPathOrDefault());
        }
        List<NoticeParseResult.ListEntry> entries = new ArrayList<>();
        for (JsonNode row : rows) {
            if (row == null || !row.isObject()) {
                continue;
            }
            NoticeParseResult.ListEntry entry = new NoticeParseResult.ListEntry();
            entry.setTitle(text(row, config.fieldName("title")));
            String rawUrl = text(row, config.fieldName("url"));
            entry.setUrl(absolutize(config, rawUrl));
            entry.setDate(text(row, config.fieldName("date")));
            entry.setCategory(text(row, config.fieldName("category")));
            entry.setContentHtml(text(row, config.fieldName("contentHtml")));
            if (entry.getUrl() != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** 相对 URL 按 urlPrefix 绝对化；绝对 URL 原样；空返回 null */
    private String absolutize(ApiFetchConfig config, String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String prefix = config.getUrlPrefix();
        if (prefix == null || prefix.isBlank()) {
            return trimmed; // 无前缀：保持原样由调用方处理
        }
        return prefix.replaceAll("/+$", "") + (trimmed.startsWith("/") ? "" : "/") + trimmed;
    }

    /** POST bodyTemplate：{page}/{size}/{categoryId}/{siteId} 占位替换（缺省模板 {"pageNo":{page},"pageSize":{size}}） */
    private String renderBody(ApiFetchConfig config, int pageNo, java.util.Map<String, String> vars) {
        String template = config.getBodyTemplate();
        if (template == null || template.isBlank()) {
            template = "{\"pageNo\":{page},\"pageSize\":{size}}";
        }
        String body = template.replace("{page}", String.valueOf(pageNo))
                .replace("{size}", String.valueOf(config.getPageSizeOrDefault()));
        if (vars != null) {
            for (java.util.Map.Entry<String, String> e : vars.entrySet()) {
                body = body.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return body;
    }

    /** URL 追加 query（已带 ? 用 &） */
    private String appendQuery(String url, String query) {
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    private String text(JsonNode row, String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        JsonNode node = row.get(field);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String v = node.isTextual() ? node.asText() : node.toString();
        return v == null || v.isBlank() ? null : v.trim();
    }
}