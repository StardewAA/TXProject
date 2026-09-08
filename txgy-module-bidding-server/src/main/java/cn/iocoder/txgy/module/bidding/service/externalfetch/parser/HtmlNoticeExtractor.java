package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] 通用 HTML 公告抽取（启发式、保守）：标题(h1→title)、正文纯文本、发布日期（复用 NoticePublishTimeResolver）、
//       附件候选（链接扩展名/文案）、发布单位（"招标人/采购人：xxx" 行启发）。
//       规则类启发一律进 warnings，测试接口供人工核对后再固化为站点专用解析器。

import cn.iocoder.txgy.module.bidding.service.noticeexternal.NoticePublishTimeResolver;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML → 公告字段抽取（通用策略 HTML_GENERIC 与站点专用策略共用）。
 */
public final class HtmlNoticeExtractor {

    /** 正文纯文本保留上限（字符）：测试/管线留档均够用，防超大页拖垮内存 */
    private static final int MAX_BODY_TEXT = 200_000;
    private static final int MAX_HTML = 4 * 1024 * 1024;
    private static final int ISSUER_SCAN_CHARS = 3000;
    /** 附件扩展名（招标文件/图纸/清单常见形态） */
    private static final Pattern ATTACH_EXT = Pattern.compile("(?i)\\.(pdf|doc|docx|xls|xlsx|zip|rar|7z)(\\?.*)?$");
    private static final Pattern ISSUER_LINE = Pattern.compile(
            "(?:^|\\s)(招标人|采购人|招标单位|项目单位|建设单位|招标代理机构|代理机构|发布单位)[\\s]*[:：][\\s]*(.{2,60}?)(?:$|[\\s,，。；;])");

    private HtmlNoticeExtractor() {
    }

    /**
     * 抽取并填充 out（不抛异常；失败以 out.status=FAILED 表达）。html 超长按 MAX_HTML 截断并告警。
     */
    public static void extract(String html, String url, NoticeParseResult out) {
        if (!StringUtils.hasText(html)) {
            out.setError("HTML 内容为空");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return;
        }
        // view-source 保存物还原（浏览器"查看源代码"包装结构）
        String decoded = HtmlViewSourceDecoder.decodeIfNeeded(html);
        String bounded = decoded.length() > MAX_HTML ? decoded.substring(0, MAX_HTML) : decoded;
        if (bounded.length() < html.length()) {
            out.getWarnings().add("HTML 超过 " + MAX_HTML + " 字符，已截断解析（仅影响统计展示，不影响规则主路径）");
        }
        Document doc = Jsoup.parse(bounded);
        // 标题：h1（含 aria/class 提示）优先，其次 <title>
        String h1 = firstText(doc.select("h1"));
        String title = StringUtils.hasText(h1) ? h1 : doc.title();
        if (StringUtils.hasText(title)) {
            title = title.trim();
            out.setTitle(title);
        }
        // 正文纯文本（去 script/style/头部导航噪声的保守处理：仅移除 script/style）
        doc.select("script,style,noscript").remove();
        String bodyText = doc.body() == null ? "" : doc.body().text();
        bodyText = bodyText.replaceAll("\\s+", " ").trim();
        if (bodyText.length() > MAX_BODY_TEXT) {
            bodyText = bodyText.substring(0, MAX_BODY_TEXT);
        }
        if (StringUtils.hasText(bodyText)) {
            out.setContentText(bodyText);
        }
        out.setContentHtml(bounded);
        out.setContentType("HTML");
        // 发布日期（启发：正文前 800 字符与标题）
        try {
            out.setPublishTime(NoticePublishTimeResolver.resolveFromText(bounded, out.getTitle()));
        } catch (RuntimeException ignored) {
            // 时间解析失败不阻断（warnings 提示）
        }
        if (out.getPublishTime() == null) {
            out.getWarnings().add("未解析到发布日期（发布日非必填列，下载器管线可再补）");
        }
        // 发布单位行启发（正文前部）
        String issuer = findIssuer(bodyText);
        if (StringUtils.hasText(issuer)) {
            out.setIssuerName(issuer);
            out.getWarnings().add("发布单位由“招标人/采购人：xxx”行启发式提取，请人工核对");
        }
        // 附件候选
        int attachmentCount = 0;
        for (Element a : doc.select("a[href]")) {
            String text = a.text() == null ? "" : a.text().trim();
            String href = a.attr("href").trim();
            boolean byExt = ATTACH_EXT.matcher(href).find();
            boolean byText = text.contains("附件") || text.contains("招标文件") || text.contains("采购文件")
                    || text.contains("谈判文件") || text.contains("下载") || text.contains("图纸");
            if (byExt || byText) {
                String name = StringUtils.hasText(text) ? text : lastSegment(href);
                Resolved resolved = absolutize(href, url);
                out.getAttachments().add(NoticeParseResult.AttachmentCandidate.of(name, resolved.url, !resolved.absolute));
                attachmentCount++;
            }
        }
        // 观测 meta
        out.getMeta().put("htmlLength", String.valueOf(bounded.length()));
        out.getMeta().put("textLength", String.valueOf(bodyText == null ? 0 : bodyText.length()));
        out.getMeta().put("titleSource", StringUtils.hasText(h1) ? "h1" : (StringUtils.hasText(doc.title()) ? "title-tag" : "none"));
        out.getMeta().put("attachmentCount", String.valueOf(attachmentCount));
    }

    private static String firstText(org.jsoup.select.Elements elements) {
        for (Element element : elements) {
            String text = element.text();
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private static String findIssuer(String bodyText) {
        if (!StringUtils.hasText(bodyText)) {
            return null;
        }
        String head = bodyText.length() > ISSUER_SCAN_CHARS ? bodyText.substring(0, ISSUER_SCAN_CHARS) : bodyText;
        // 中文标点后可能无空白直接接名称，改用宽松跨行扫描（把中文逗号句号当分隔重试）
        String probe = head;
        for (int attempt = 0; attempt < 2; attempt++) {
            Matcher matcher = ISSUER_LINE.matcher(probe);
            if (matcher.find()) {
                String name = matcher.group(2).trim();
                // 名称后若整行太长（正文粘连），截到常见结尾词
                int cut = firstCut(name);
                return cut > 0 ? name.substring(0, cut) : name;
            }
            // 第二次尝试：移除中文标点后再匹配
            probe = probe.replaceAll("[，。；；、]", " ");
        }
        return null;
    }

    private static int firstCut(String name) {
        String[] markers = {"招标公告", "招标编号", "项目编号", "本招标项目", "一、", "（招标编号"};
        int earliest = -1;
        for (String marker : markers) {
            int at = name.indexOf(marker);
            if (at >= 0 && (earliest < 0 || at < earliest)) {
                earliest = at;
            }
        }
        return earliest;
    }

    private static String lastSegment(String href) {
        String cleaned = href.split("[?#]")[0];
        int slash = cleaned.lastIndexOf('/');
        String last = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        return StringUtils.hasText(last) ? last : "附件";
    }

    private static Resolved absolutize(String href, String baseUrl) {
        try {
            URI base = StringUtils.hasText(baseUrl) ? URI.create(baseUrl) : null;
            URI target = URI.create(href);
            if (base != null && !target.isAbsolute()) {
                return new Resolved(base.resolve(target).toString(), true);
            }
            return new Resolved(target.isAbsolute() ? target.toString() : href, target.isAbsolute());
        } catch (RuntimeException e) {
            return new Resolved(href, false);
        }
    }

    private record Resolved(String url, boolean absolute) {
    }

}
