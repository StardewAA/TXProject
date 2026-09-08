package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] 秦源招标电子招标平台（qyzb.shccmg.com）站点解析器（QYZB_SHCCMG）—— 规则基于 2026-09-07 真实详情页样本校准：
//       页面 = 导航 + "详情 返回列表 {标题}" + "发布时间：yyyy-MM-dd" + 正文（标题 + （招标编号：xxx） + 一、…段）。
//       输入可为原始 HTML 或浏览器"查看源代码"保存物（HtmlViewSourceDecoder 自动还原）。

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QYZB（qyzb.shccmg.com 秦源平台）详情页解析器 v1。
 */
@Component
public class QyzNoticeParser implements NoticeParser {

    private static final String SITE_HOST = "qyzb.shccmg.com";

    private static final Pattern PUBLISH_LINE = Pattern.compile(
            "发布时间\\s*[:：]?\\s*(20\\d{2})-(\\d{1,2})-(\\d{1,2})(?:\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?");
    private static final Pattern RAW_DATETIME = Pattern.compile("-->\\s*(20\\d{2})-(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{2}):(\\d{2})\\s*-->");
    private static final Pattern BID_CODE = Pattern.compile(
            "(?:招标编号|采购编号)\\s*[:：]?\\s*([0-9A-Za-z][0-9A-Za-z\\-]{4,39})");
    private static final Pattern ISSUER = Pattern.compile(
            "(招标人|采购人)\\s*[:：]\\s*([^\\s]{2,60})");

    @Override
    public String getParserCode() {
        return "QYZB_SHCCMG";
    }

    @Override
    public String getParserName() {
        return "秦源招标电子招标平台解析器（qyzb.shccmg.com）";
    }

    @Override
    public String getDescription() {
        return "QYZB 详情页定向规则 v1（2026-09 样本校准）：导航标题/发布时间/招标编号/招标人/正文区间抽取；"
                + "支持浏览器查看源代码保存物自动还原；列表页/更多栏目类型待样本补充。";
    }

    @Override
    public List<String> supportedPayloadTypes() {
        return List.of("HTML");
    }

    @Override
    public NoticeParseResult parse(ParseInput input) {
        String decoded = HtmlViewSourceDecoder.decodeIfNeeded(input.getContent());
        // 1) 先走通用抽取（HTML 留档/附件候选/文本兜底）
        NoticeParseResult out = new NoticeParseResult();
        out.setParserCode(getParserCode());
        out.setParserName(getParserName());
        HtmlNoticeExtractor.extract(decoded, input.getUrl(), out);
        // 2) QYZB 定向覆写
        try {
            applyQyzRules(decoded, input.getUrl(), out);
        } catch (RuntimeException e) {
            out.getWarnings().add("QYZB 定向规则执行异常，保留通用抽取结果：" + e.getMessage());
        }
        if (out.getOrigUrl() == null && StringUtils.hasText(input.getUrl())) {
            out.setOrigUrl(input.getUrl());
        }
        out.getMeta().putIfAbsent("site", SITE_HOST);
        if (out.getStatus() == null || out.getStatus() == NoticeParseResult.Status.SUCCESS
                || out.getStatus() == NoticeParseResult.Status.PARTIAL) {
            out.finalizeStatus();
        }
        return out;
    }

    /** QYZB 详情页定向字段（基于真实样本结构）。 */
    private void applyQyzRules(String html, String url, NoticeParseResult out) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        String text = doc.body() == null ? doc.text() : doc.body().wholeText();
        if (!StringUtils.hasText(text)) {
            return;
        }
        // 标题：“返回列表”后的首个非空行（详情区第一标题，常与正文标题一致）
        String[] lines = text.split("\\r?\\n");
        String title = null;
        for (int i = 0; i < lines.length && title == null; i++) {
            String line = lines[i].trim();
            if (line.contains("返回列表")) {
                for (int j = i + 1; j < Math.min(lines.length, i + 4); j++) {
                    String candidate = lines[j].trim();
                    if (candidate.length() >= 4 && candidate.length() <= 150 && !candidate.contains("供应商征集")) {
                        title = candidate;
                        break;
                    }
                }
            }
        }
        if (!StringUtils.hasText(title)) {
            // 兜底：首个含“公告/公示/采购”字样行
            for (int i = 0; i < Math.min(lines.length, 12); i++) {
                String line = lines[i].trim();
                if (line.length() >= 6 && line.length() <= 150
                        && (line.contains("公告") || line.contains("公示") || line.contains("采购"))) {
                    title = line;
                    break;
                }
            }
        }
        if (StringUtils.hasText(title)) {
            out.setTitle(title.trim());
        }
        // 发布时间：“发布时间：yyyy-MM-dd”（带时分兜底 RAW_DATETIME）
        LocalDateTime publish = matchPublish(PUBLISH_LINE, text);
        if (publish == null) {
            publish = matchPublish(RAW_DATETIME, text);
        }
        if (publish != null) {
            out.setPublishTime(publish);
        }
        // 招标编号/采购编号 → tenderProjectCode（跟踪维度：按招标编号关注招标项目）
        Matcher codeMatcher = BID_CODE.matcher(text);
        if (codeMatcher.find()) {
            out.setTenderProjectCode(codeMatcher.group(1));
            out.getMeta().put("qyzb.bidCodeSource", "招标编号/采购编号");
        }
        // 招标人/采购人（联系方式段）
        Matcher issuerMatcher = ISSUER.matcher(text);
        if (issuerMatcher.find()) {
            String name = issuerMatcher.group(2).trim();
            int cut = name.length();
            for (String marker : new String[]{"地", "联", "电", "，", ",", "。"}) {
                int at = name.indexOf(marker);
                if (at >= 0 && at < cut) {
                    cut = at;
                }
            }
            out.setIssuerName(name.substring(0, Math.max(2, cut)).trim());
        }
        // 栏目（URL /webfile/<cat>/yyyyMMdd/<id>.html）
        if (StringUtils.hasText(url)) {
            Matcher cat = Pattern.compile("/webfile/([^/]+)/").matcher(url);
            if (cat.find()) {
                out.getMeta().put("qyzb.category", cat.group(1));
            }
        }
        // 正文区间：起点=正文标题（标题最后一次出现；找不到则回退“发布时间”行），
        // 终点=正文后第一个“供应商征集公告”（推荐区）等标记；结果上限 3000 字
        String titleFragment = title == null ? null : title.trim();
        int pubAt = text.indexOf("发布时间");
        int lastTitle = titleFragment == null ? -1 : text.lastIndexOf(titleFragment);
        int from;
        if (lastTitle >= 0) {
            from = lastTitle;
        } else if (pubAt >= 0) {
            int lineStart = text.lastIndexOf('\n', Math.max(0, pubAt - 1));
            from = lineStart >= 0 ? lineStart + 1 : pubAt;
        } else {
            from = 0;
        }
        int end = text.length();
        int skipFrom = Math.min(text.length(), from + (titleFragment == null ? 0 : Math.max(20, titleFragment.length())));
        for (String marker : new String[]{"供应商征集公告", "相关公告", "浏览次数", "秦源资讯", "常见问题解答"}) {
            int at = text.indexOf(marker, Math.max(skipFrom, from + 10));
            if (at > from && at < end) {
                end = at;
            }
        }
        if (from < text.length()) {
            String body = text.substring(from, Math.min(end, text.length())).trim();
            if (StringUtils.hasText(body)) {
                out.setContentText(body.length() > 3000 ? body.substring(0, 3000) : body);
            }
        }
        // 公告类型（标题关键词）
        String rawType = inferType(title == null ? text : title);
        if (rawType != null) {
            out.setNoticeTypeRaw(rawType);
        }
    }

    private static String inferType(String text) {
        if (text.contains("中标结果公示")) {
            return "中标结果公示";
        }
        if (text.contains("中标候选人公示")) {
            return "中标候选人公示";
        }
        if (text.contains("中标公告")) {
            return "中标公告";
        }
        if (text.contains("变更公告") || text.contains("更正公告")) {
            return "变更公告";
        }
        if (text.contains("谈判采购") || text.contains("询比采购") || text.contains("非招标采购")) {
            return "谈判采购公告";
        }
        if (text.contains("招标公告")) {
            return "招标公告";
        }
        if (text.contains("结果公告")) {
            return "结果公告";
        }
        return null;
    }

    private static LocalDateTime matchPublish(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            try {
                int hour = matcher.groupCount() >= 4 && matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;
                int minute = matcher.groupCount() >= 5 && matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;
                int second = matcher.groupCount() >= 6 && matcher.group(6) != null ? Integer.parseInt(matcher.group(6)) : 0;
                return LocalDateTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)), hour, minute, second);
            } catch (RuntimeException ignored) {
                // 非法日期继续找下一处
            }
        }
        return null;
    }

}
