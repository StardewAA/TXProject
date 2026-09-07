package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] 文本型公告（PDF 抽取文本/纯文本）字段启发：标题行/编号/日期/招标人；规则类结果进 warnings。
//       站点版式差异在站点专用解析器内固化（本工具=通用兜底）。

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF/文本公告 → 公告字段抽取（通用启发）。
 */
public final class PdfTextNoticeExtractor {

    private static final int MAX_TEXT = 200_000;
    /** 编号标签空白容忍：普通空白/换行/全角空格（PDFBox 抽取常见 　） */
    private static final String SP = "[\\s\\u3000]*";
    private static final Pattern CODE = Pattern.compile(
            "(?:招标" + SP + "编号|采购" + SP + "编号|标段" + SP + "编号)" + SP + "[:：]?" + SP + "([0-9A-Za-z][0-9A-Za-z\\-]{4,39})");
    private static final Pattern PUBLISH_LINE = Pattern.compile(
            "(?:发布日期|发布时间|公告时间|公示时间)\\s*[:：]?\\s*(20\\d{2})[-年/.](\\d{1,2})[-月/.](\\d{1,2})日?");
    private static final Pattern DATE = Pattern.compile("(20\\d{2})[-年/.](\\d{1,2})[-月/.](\\d{1,2})日?");
    private static final Pattern ISSUER = Pattern.compile(
            "(招标人|采购人|发包人|招标单位)\s*[:：]\s*([^\\s]{2,60})");
    private static final Pattern TENDER_CODE = Pattern.compile(
            "(?:招标项目" + SP + "编号|项目" + SP + "编号)" + SP + "[:：]?" + SP + "([0-9A-Za-z][0-9A-Za-z\\-]{5,39})");

    private PdfTextNoticeExtractor() {
    }

    /**
     * 从文本抽取并填充 out；返回文本（供上层 contentText 使用）。
     */
    public static String extract(String pdfText, NoticeParseResult out) {
        String text = pdfText == null ? "" : pdfText;
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
        if (text.isBlank()) {
            out.setError("PDF 未抽取到文本（扫描件/纯图 PDF 无文字层）");
            out.setStatus(NoticeParseResult.Status.FAILED);
            return text;
        }
        out.setContentText(text);
        out.setContentType("PDF");
        String[] lines = text.split("\\r?\\n");
        // 标题：前 15 行内第一条“像标题”的行：排除编号行/（…）行/小节号行；
        // 若标题行尾缺“公告/公示”词且下一行是 2~12 字公告词短行 → 拼接（PDF 常见标题断行排版）
        String title = null;
        for (int i = 0; i < Math.min(lines.length, 15) && title == null; i++) {
            String line = lines[i].trim();
            if (!isTitleCandidate(line)) {
                continue;
            }
            title = line;
            if (!line.contains("公告") && !line.contains("公示") && i + 1 < lines.length) {
                String next = lines[i + 1].trim();
                if (next.length() >= 2 && next.length() <= 14
                        && (next.contains("公告") || next.contains("公示") || next.contains("通知"))) {
                    title = line + next;
                    out.getMeta().put("titleJoined", "true");
                }
            }
        }
        if (StringUtils.hasText(title)) {
            out.setTitle(title);
        } else {
            for (int i = 0; i < Math.min(lines.length, 6); i++) {
                if (lines[i].trim().length() >= 10) {
                    out.setTitle(lines[i].trim());
                    break;
                }
            }
        }
        // 编号：招标/采购编号 → projectCode；另有“招标项目编号/项目编号”→ tenderProjectCode（跟踪维度）
        Matcher codeMatcher = CODE.matcher(text);
        if (codeMatcher.find()) {
            out.setProjectCode(codeMatcher.group(1));
        }
        Matcher tenderMatcher = TENDER_CODE.matcher(text);
        if (tenderMatcher.find()) {
            out.setTenderProjectCode(tenderMatcher.group(1));
        }
        // 时间：优先“发布日期/发布时间：”行；无标签时取全文最后一个合理日期（公告落款日一般在文末）
        LocalDateTime publish = first(PUBLISH_LINE, text);
        if (publish == null) {
            publish = last(DATE, text);
        }
        if (publish != null) {
            out.setPublishTime(publish);
        } else {
            out.getWarnings().add("未解析到日期（文本中无 yyyy 年/yyyy-MM-dd 形态日期）");
        }
        // 招标人（首行命中）
        Matcher issuerMatcher = ISSUER.matcher(text);
        if (issuerMatcher.find()) {
            out.setIssuerName(issuerMatcher.group(2).trim());
        }
        out.getMeta().put("textLength", String.valueOf(text.length()));
        out.getMeta().put("lineCount", String.valueOf(lines.length));
        return text;
    }

    /** 标题候选行：非空；非（…）/纯编号行/小节行（如 一、/1.）；含中文或字母数字且长度 6~120 */
    private static boolean isTitleCandidate(String line) {
        if (line.length() < 6 || line.length() > 120) {
            return false;
        }
        if (line.startsWith("（") || line.startsWith("(")) {
            return false;
        }
        if (line.matches("\\d+\\.\\s*.*") || line.matches("[一二三四五六七八九十]+、.*")) {
            return false;
        }
        return true;
    }

    private static LocalDateTime first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            LocalDateTime candidate = toDateTime(matcher);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /** 最后一个合理日期（公告落款日期在文末的版式；窗口 2000-01-01 ~ now+1d） */
    private static LocalDateTime last(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        LocalDateTime found = null;
        while (matcher.find()) {
            LocalDateTime candidate = toDateTime(matcher);
            if (candidate != null) {
                found = candidate;
            }
        }
        return found;
    }

    private static LocalDateTime toDateTime(Matcher matcher) {
        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            LocalDate date = LocalDate.of(year, month, day);
            if (!date.isBefore(LocalDate.of(2000, 1, 1)) && !date.isAfter(LocalDate.now().plusDays(1))) {
                return date.atStartOfDay();
            }
        } catch (RuntimeException ignored) {
            // 非法日期跳过
        }
        return null;
    }

}
