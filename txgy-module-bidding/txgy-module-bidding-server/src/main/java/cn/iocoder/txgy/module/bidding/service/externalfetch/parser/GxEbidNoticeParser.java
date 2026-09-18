package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] 国信e采（山西）gx.e-bidding.org 站点解析器（2026-09-07 真实样本校准）：
//   列表页 category/iframe.html ul.newslist → entries；详情页 pdf.js iframe → PDF 直链（openFileById，双重 URL 编码）。

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class GxEbidNoticeParser implements NoticeParser {

    private static final String PDFJS_MARKER = "viewer.html?file=";

    @Override public String getParserCode() { return "GX_E_BIDDING"; }
    @Override public String getParserName() { return "国信e采（山西）解析器（gx.e-bidding.org）"; }
    @Override public String getDescription() { return "国信站点解析 v2：列表 ul.newslist → entries；详情 pdf.js → PDF 直链；正文在 PDF 内由 PDF_TEXT_GENERIC 解析。"; }
    @Override public List<String> supportedPayloadTypes() { return List.of("HTML"); }

    @Override
    public NoticeParseResult parse(ParseInput input) {
        NoticeParseResult out = new NoticeParseResult();
        out.setParserCode(getParserCode());
        out.setParserName(getParserName());
        String decoded = HtmlViewSourceDecoder.decodeIfNeeded(input.getContent());
        if (decoded == null || decoded.isBlank()) {
            out.setError("HTML 内容为空");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return out;
        }
        Document doc = Jsoup.parse(decoded);
        out.setContentHtml(decoded);
        String pdfUrl = findPdfJsUrl(decoded);
        if (pdfUrl != null) {
            out.setPageType("DETAIL_PDF");
            out.setContentType("PDF");
            out.setOrigUrl(input.getUrl());
            out.getMeta().put("pdfDownloadUrl", pdfUrl);
            out.getMeta().put("pdfSource", "pdfjs-viewer");
            out.getAttachments().add(NoticeParseResult.AttachmentCandidate.of("公告PDF", pdfUrl, false));
            out.getWarnings().add("详情为 PDF：标题/正文在 PDF 内，直链见 meta.pdfDownloadUrl，随后用 PDF_TEXT_GENERIC 解析");
            out.setStatus(NoticeParseResult.Status.SUCCESS);
            return out;
        }
        List<NoticeParseResult.ListEntry> entries = new ArrayList<>();
        // 兼容 ul.newslist > li 与 iframe.html 裸 <li>（2026-09-09 实测：外层页无条目，条目在 iframe.html 的裸 li）
        for (Element li : doc.select("li:has(> a[href])")) {
            Element a = li.selectFirst("a[href]");
            if (a == null) continue;
            String href = a.absUrl("href");
            if (href.isEmpty()) href = a.attr("href");
            String title = a.hasAttr("title") && !a.attr("title").isBlank() ? a.attr("title").trim() : a.text().trim();
            if (title.isEmpty() || title.length() < 4) {
                Element h5 = a.selectFirst("h5");
                if (h5 != null) title = h5.text().trim();
            }
            if (title.isEmpty()) continue;
            NoticeParseResult.ListEntry entry = new NoticeParseResult.ListEntry();
            entry.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
            entry.setUrl(href);
            entry.setDate(pickDate(href, li.text()));
            entries.add(entry);
        }
        if (!entries.isEmpty()) {
            out.setEntries(entries);
            out.setPageType("LIST");
            out.getMeta().put("listEntryCount", String.valueOf(entries.size()));
            out.setStatus(NoticeParseResult.Status.SUCCESS);
            out.getWarnings().add("输入为列表页：entries 已解析 " + entries.size() + " 条");
            return out;
        }
        out.setPageType("UNKNOWN");
        out.getMeta().put("listEntryCount", "0");
        out.getWarnings().add("未发现 pdf.js 详情或 ul.newslist 条目：可能为栏目壳页（列表数据走 JSON 接口 res.rows）或结构变化");
        out.finalizeStatus();
        return out;
    }

    /** 提取 pdf.js viewer file 参数并双重解码为真实 PDF URL。 */
    static String findPdfJsUrl(String html) {
        if (html == null) return null;
        String text = html;
        int idx = text.indexOf(PDFJS_MARKER);
        if (idx < 0) {
            int lower = text.toLowerCase().indexOf(PDFJS_MARKER);
            if (lower < 0) return null;
            text = text.substring(lower + PDFJS_MARKER.length());
        } else {
            text = text.substring(idx + PDFJS_MARKER.length());
        }
        int end = text.length();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 38 || c == 34 || c == 62 || c == 32 || c == 10 || c == 13 || c == 9) { end = i; break; } // & " > space \n \r \t
        }
        String encoded = text.substring(0, end).trim();
        if (encoded.isEmpty()) return null;
        try {
            String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            if (decoded.contains("%")) decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            return decoded;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 条目日期：详情 URL 段 /sdny_bulletin/{yyyy-MM-dd}/ 优先，其次行内最后一个 yyyy-MM-dd。 */
    private String pickDate(String href, String rowText) {
        String urlDate = dateOf(href == null ? "" : href);
        if (urlDate != null) return urlDate;
        String found = null;
        String rest = rowText == null ? "" : rowText;
        while (true) {
            int at = indexOfDate(rest);
            if (at < 0) break;
            found = rest.substring(at, at + 10);
            rest = rest.substring(at + 10);
        }
        return found;
    }

    private static String dateOf(String text) {
        int at = indexOfDate(text);
        return at < 0 ? null : text.substring(at, at + 10);
    }

    /** 定位 yyyy-MM-dd（20xx-xx-xx，10 字符；宽容月份/日位数不做校验） */
    private static int indexOfDate(String text) {
        if (text == null) return -1;
        for (int i = 0; i + 10 <= text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 48 && c <= 57 && text.charAt(i + 4) == 45 && text.charAt(i + 7) == 45) return i;
        }
        return -1;
    }

}
