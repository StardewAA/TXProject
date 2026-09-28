package cn.iocoder.txgy.module.bidding.service.noticeexternal;
// [依据] 前端配合说明 BR-5：外部公告发布时间回填——采集落库时若采集侧未给发布时间（历史抓取常见），
// 从 HTML 原文/标题尽力解析“发布日期”并回填 publish_time；解析不到才取抓取时间（DO 列注释口径：解析失败取抓取时间）。
// 权威要求：招标公告应载明发布/截止等时间（《招标投标法》第16条、10号令 第5条：公告应载明获取招标文件的时间地点）。

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部公告发布日期解析（启发式、尽力而为）：
 * 支持 yyyy-MM-dd / yyyy/MM/dd / yyyy.MM.dd / yyyy年M月d日（可带 HH:mm[:ss]），
 * 仅在正文（去标签后）前 800 字符与标题内顺序取第一个“合理过去时间”（2000-01-01 ~ now+1d），
 * 防止把报名截止等未来时间误判为发布日期；无命中返回 null（由调用方兜底取抓取时间）。
 */
public final class NoticePublishTimeResolver {

    private static final int HEAD_CHARS = 800;
    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

    private static final Pattern[] PATTERNS = {
            // yyyy-MM-dd [HH:mm[:ss]]
            Pattern.compile("(?:^|[^0-9])(20\\d{2})-(\\d{1,2})-(\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?"),
            // yyyy/MM/dd
            Pattern.compile("(?:^|[^0-9])(20\\d{2})/(\\d{1,2})/(\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?"),
            // yyyy.MM.dd
            Pattern.compile("(?:^|[^0-9])(20\\d{2})\\.(\\d{1,2})\\.(\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?"),
            // yyyy年M月d日[ HH:mm[:ss]]
            Pattern.compile("(20\\d{2})年(\\d{1,2})月(\\d{1,2})日(?:\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?"),
    };

    private NoticePublishTimeResolver() {
    }

    /**
     * 从 HTML 原文与标题解析发布日期。
     *
     * @param contentHtml 正文 HTML（可为 null；去标签后取前 HEAD_CHARS 字符）
     * @param title       标题（可为 null；作为补充扫描源）
     * @return 解析到的发布时间（日期在 2000-01-01 ~ now+1d 窗内），无命中返回 null
     */
    public static LocalDateTime resolveFromText(String contentHtml, String title) {
        LocalDateTime found = null;
        String htmlHead = null;
        if (StringUtils.hasText(contentHtml)) {
            String text = stripTags(contentHtml);
            if (text.length() > HEAD_CHARS) {
                text = text.substring(0, HEAD_CHARS);
            }
            if (StringUtils.hasText(text)) {
                htmlHead = text;
            }
        }
        if (htmlHead != null) {
            found = firstReasonable(htmlHead);
        }
        if (found == null && StringUtils.hasText(title)) {
            found = firstReasonable(title);
        }
        return found;
    }

    private static String stripTags(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("&nbsp;?", " ").replaceAll("\\s+", " ");
    }

    private static LocalDateTime firstReasonable(String text) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime upper = now.plusDays(1);
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                LocalDateTime candidate = toDateTime(matcher);
                if (candidate != null && !candidate.isBefore(EARLIEST.atStartOfDay()) && !candidate.isAfter(upper)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static LocalDateTime toDateTime(Matcher matcher) {
        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                return null;
            }
            int hour = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;
            int minute = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;
            int second = matcher.group(6) != null ? Integer.parseInt(matcher.group(6)) : 0;
            if (hour > 23 || minute > 59 || second > 59) {
                return null;
            }
            return LocalDateTime.of(year, month, day, hour, minute, second);
        } catch (RuntimeException e) {
            return null; // 2026-02-30 等非法日期按无命中处理
        }
    }

}
