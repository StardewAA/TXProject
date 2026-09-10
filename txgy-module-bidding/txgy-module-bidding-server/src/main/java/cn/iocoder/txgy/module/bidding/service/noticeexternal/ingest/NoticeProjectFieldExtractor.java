package cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * 外部公告【项目维度】字段增强提取器。
 *
 * <p>背景与依据：2026-09-09 全链路核查发现，存量 ACTIVE 公告 20 条中 project_name 覆盖为 0、project_code 仅 4 条——
 * 采集解析只按字段别名表取结构化值，多数来源站正文并不提供结构化的“项目名称”标签，导致项目维度无法被关注/聚合。
 * 本类负责从【标题 + 正文原文】推导 projectCode / projectName，供：
 * ① 入库增强（buildNotice 对空值回填，见 ExternalNoticeIngestServiceImpl）；② 存量回填（backfill，幂等只补空）。
 *
 * <p>提取规则与理由（依据真实样本：陕西延长石油 DYZB2025-11-G141 系列公告正文）：
 * 1. 编号优先从“（招标编号：xxx）”类括号标签取（正文首段常用“{项目名}（招标编号：DYZBxxxx-xx-Gxxx）”形态）；
 * 2. 项目名 = 编号括号前的同句文本，裁掉尾部文种后缀（招标公告/公示…），避免把文种词混入项目名；
 * 3. 无括号标签时回退“项目名称：/项目编号：”独立标签；再回退标题去文种后缀（保守：要求含中文且长度≥6）。
 * 4. 无法可靠识别一律返回 null（宁可留空不瞎猜，避免脏值污染关注键——历史脏值“NULL”字面量教训见 NoticeExternalServiceImpl）。
 */
public final class NoticeProjectFieldExtractor {

    private NoticeProjectFieldExtractor() {
    }

    /** 提取结果（可能两项均为 null=未能识别，调用方应保留原值）。 */
    public record ProjectFields(String projectCode, String projectName) {
    }

    /** 括号标签形态：（招标编号：xxx）（项目编号：xxx）…，支持全/半角括号与冒号。 */
    private static final Pattern PAREN_LABEL = Pattern.compile(
            "[（(]\\s*(招标编号|项目编号|招标项目编号|采购编号|项目编码)\\s*[:：]\\s*([0-9A-Za-z][0-9A-Za-z_/.:（）()-]{3,63})\\s*[）)]");
    /** 独立标签形态：招标编号：xxx（无括号，正文以标签冒号分行时）。 */
    private static final Pattern BARE_LABEL = Pattern.compile(
            "(?:招标编号|项目编号|招标项目编号|采购编号|项目编码)\\s*[:：]\\s*([0-9A-Za-z][0-9A-Za-z_/.:（）()-]{3,63})");
    /** 项目名称独立标签（部分来源会提供）。 */
    private static final Pattern NAME_LABEL = Pattern.compile(
            "项目名称\\s*[:：]\\s*([^\\n；;。]{4,120})");

    /** 文种后缀（按长→短裁剪，避免残余半截词）。 */
    private static final String[] SUFFIXES = {
            "公开招标公告", "招标采购公告", "竞争性谈判公告", "中标候选人公示", "中标结果公示", "招标公告", "采购公告", "磋商公告", "询价公告", "资格预审公告", "更正公告", "变更公告", "公示", "公告"
    };

    /**
     * 从标题+正文推导项目编号/项目名称。
     *
     * @param title   公告标题（可能含文种，仅作回退来源）
     * @param content HTML 或纯文本正文；HTML 会先扁平化为文本；可为 null
     * @return 识别结果；无把握时字段为 null
     */
    public static ProjectFields extract(String title, String content) {
        String text = flatten(content);
        String code = null;
        String name = null;
        // ---- 编号：括号标签优先（正文常见形态），无则独立标签 ----
        Matcher m = PAREN_LABEL.matcher(text);
        int codeAt = -1;
        if (m.find()) {
            code = m.group(2).trim();
            codeAt = m.start();
        } else {
            Matcher b = BARE_LABEL.matcher(text);
            if (b.find()) {
                code = b.group(1).trim();
                codeAt = b.start();
            }
        }
        if (code != null) {
            code = code.replace("（", "").replace("）", "").trim();
            // 编号短到不可信（<4）丢弃，防把“年份”误当编号
            if (code.length() < 4) {
                code = null;
            }
        }
        // ---- 项目名：1) 编号括号前的同句文本（最可靠：正文首段“{项目名}（招标编号：xxx）”精确分段）→
        //       2) “项目名称：”独立标签（若与括号前缀并存，优先括号前缀——标签常跨句截到长引文，如真实样本
        //       污染值“标题 + 空格 + 正文项目名”合并串，2026-09-09 回填首轮实测）→ 3) 标题回退 ----
        if (codeAt >= 0) {
            name = nameBeforeCode(text, codeAt);
        }
        if (name == null) {
            Matcher nl = NAME_LABEL.matcher(text);
            if (nl.find()) {
                name = sanitizeName(nl.group(1));
            }
        }
        if (name == null) {
            name = fromTitle(title);
        }
        return new ProjectFields(code, name);
    }

    /** 编号括号前同句文本裁剪：从最近句读符后到括号前，去文种后缀。 */
    private static String nameBeforeCode(String text, int codeAt) {
        int start = Math.max(0, lastSentenceBreak(text, codeAt));
        String seg = text.substring(start, codeAt).trim();
        // 段落级前言（“一、…”“本…已批准…”）出现在段首时，整句先保留；尾部若明显拖带前置语再裁到最近逗号
        String candidate = stripSuffixes(seg);
        if (candidate.length() >= 4) {
            seg = candidate;
        } else if (seg.contains("，")) {
            seg = seg.substring(seg.lastIndexOf("，") + 1).trim();
            seg = stripSuffixes(seg);
        }
        String cleaned = sanitizeName(seg);
        // 长度门槛≥6（与标题回退一致）：无括号的“招标编号：”前往往只是句首引导语（如“本公告依据”），
        // 过短前缀视为无法识别，回退标题；避免把引导语当项目名入库
        if (cleaned == null || cleaned.length() < 6) {
            return null;
        }
        return cleaned;
    }

    private static int lastSentenceBreak(String text, int from) {
        int cut = -1;
        for (char c : new char[]{'。', '；', '\n', '！', '？'}) {
            cut = Math.max(cut, text.lastIndexOf(c, from - 1));
        }
        return cut + 1;
    }

    /** 标题回退：去文种后缀、去“关于”前缀；要求含中文且长度≥6（过短视为无法识别）。 */
    private static String fromTitle(String title) {
        if (title == null) {
            return null;
        }
        String t = title.trim();
        if (t.startsWith("关于")) {
            t = t.substring(2).trim();
        }
        String stripped = sanitizeName(stripSuffixes(t));
        if (stripped == null || stripped.length() < 6) {
            return null;
        }
        return stripped;
    }

    private static String stripSuffixes(String s) {
        String cur = s.trim();
        boolean changed;
        do {
            changed = false;
            for (String suf : SUFFIXES) {
                if (cur.endsWith(suf)) {
                    cur = cur.substring(0, cur.length() - suf.length()).trim();
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return cur;
    }

    /** 清洗：去掉残留 HTML/多余空白；需含中文才认可（纯编号/纯符号不算项目名）。 */
    private static String sanitizeName(String s) {
        if (s == null) {
            return null;
        }
        String v = s.replaceAll("\\s+", " ").replaceAll("[<>\"']", "").trim();
        if (v.isEmpty() || !hasCjk(v) || v.length() > 120) {
            return null;
        }
        return v;
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    /** 正文扁平化：HTML→文本，非 HTML 原样返回。
     * 关键：按【块级元素逐个取文本并以换行拼接】而非整页 text()——整页 text() 会把 h1/h2 标题与正文用空格粘连，
     * 使“编号括号前项目名”误含标题（2026-09-09 实测污染：标题+空格+正文项目名合并串）；换行则充当句子边界。 */
    private static String flatten(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (content.indexOf('<') < 0) {
            return content;
        }
        Document doc = Jsoup.parseBodyFragment(content);
        StringBuilder sb = new StringBuilder();
        walkBlocks(doc.body(), sb);
        return sb.toString();
    }

    /** 递归块级扁平化：div/section/table 等容器递归下沉，p/h1-h6/li/td 等叶子块各占一行（叶子块内联标签随 text() 合并）。 */
    private static void walkBlocks(Element root, StringBuilder sb) {
        for (Element e : root.children()) {
            String tag = e.tagName();
            if ("div".equals(tag) || "section".equals(tag) || "article".equals(tag)
                    || "main".equals(tag) || "tbody".equals(tag) || "thead".equals(tag) || "table".equals(tag)) {
                walkBlocks(e, sb);
            } else {
                sb.append(e.text()).append('\n');
            }
        }
    }
}
