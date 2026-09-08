package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] 秦源（qyzb.shccmg.com）栏目 JSON 接口解析（2026-09-07 用户提供响应样本校准）：
//   {code:0,res:{total,rows:[{title,categoryId,url:"/3ywgg/20260827/{id}.html",text(全文HTML),publishDate,categoryName,...}],pageNo,pageCount,pageSize,hasNext}}
//   rows.url 为栏目相对路径 → 详情 = https://qyzb.shccmg.com/cms/default/webfile + url（下载器按详情 HTML 复用 QYZB_SHCCMG 解析）。

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class QyzJsonListParser implements NoticeParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "https://qyzb.shccmg.com/cms/default/webfile";

    @Override public String getParserCode() { return "QYZB_JSON"; }
    @Override public String getParserName() { return "秦源栏目 JSON 接口解析器（qyzb.shccmg.com）"; }
    @Override public String getDescription() { return "解析秦源栏目接口响应（res.rows）：标题/详情URL（/cms/default/webfile+url）/日期/栏目；rows.text 为全文 HTML（下载器可直存或走详情解析）。"; }
    @Override public List<String> supportedPayloadTypes() { return List.of("JSON"); }

    @Override
    public NoticeParseResult parse(ParseInput input) {
        NoticeParseResult out = new NoticeParseResult();
        out.setParserCode(getParserCode());
        out.setParserName(getParserName());
        if (input.getContent() == null || input.getContent().isBlank()) {
            out.setError("JSON 内容为空");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(input.getContent());
        } catch (Exception e) {
            out.setError("JSON 解析失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        JsonNode rows = root == null ? null : root.path("res").path("rows");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            out.setPageType("UNKNOWN");
            out.getMeta().put("listEntryCount", "0");
            out.getWarnings().add("未在 JSON 的 res.rows 中找到条目（接口结构变化或非列表响应）");
            out.finalizeStatus();
            return out;
        }
        List<NoticeParseResult.ListEntry> entries = new ArrayList<>();
        for (JsonNode row : rows) {
            String title = text(row.get("title"));
            if (title == null || title.isBlank()) continue;
            NoticeParseResult.ListEntry entry = new NoticeParseResult.ListEntry();
            entry.setTitle(title.trim());
            String rel = text(row.get("url"));
            if (rel != null && !rel.isBlank()) {
                entry.setUrl(rel.startsWith("http") ? rel : BASE + (rel.startsWith("/") ? rel : "/" + rel));
            }
            entry.setDate(dateOf(text(row.get("publishDate"))));
            entry.setCategory(text(row.get("categoryName")));
            entry.setContentHtml(text(row.get("text"))); // 全文 HTML（下载器直采）
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            out.setPageType("UNKNOWN");
            out.getMeta().put("listEntryCount", "0");
            out.getWarnings().add("res.rows 条目缺 title/url，无法建任务");
            out.finalizeStatus();
            return out;
        }
        out.setEntries(entries);
        out.setPageType("LIST");
        out.getMeta().put("listEntryCount", String.valueOf(entries.size()));
        out.getMeta().put("total", String.valueOf(root.path("res").path("total").asLong()));
        out.getMeta().put("pageNo", String.valueOf(root.path("res").path("pageNo").asLong()));
        out.getMeta().put("hasNext", String.valueOf(root.path("res").path("hasNext").asBoolean()));
        out.setStatus(NoticeParseResult.Status.SUCCESS);
        out.getWarnings().add("输入为秦源栏目 JSON：entries " + entries.size() + " 条（详情 URL 已绝对化；rows.text 全文可直存）");
        return out;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String value = node.isTextual() ? node.asText() : node.toString();
        return value == null ? null : value.trim();
    }

    /** publishDate（2026-08-27T14:49:07.000+0800）→ yyyy-MM-dd */
    private static String dateOf(String publishDate) {
        if (publishDate == null || publishDate.length() < 10) return null;
        String head = publishDate.substring(0, 10).toLowerCase(Locale.ROOT);
        return head.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") ? head : null;
    }

}
