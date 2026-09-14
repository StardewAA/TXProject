package cn.iocoder.txgy.module.bidding.service.noticeexternal;
// [依据] BR-5 发布时间回填：原文发布日期解析（支持 ISO/斜杠/点/中文日期；窗口 2000~now+1d 防未来截止时间误判）。
// 测试用动态“过去”日期（now-5 天），避免被未来窗口过滤。

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NoticePublishTimeResolverTest {

    /** 过去第 5 天的日期（窗口内且远离边界）。 */
    private LocalDate pastDay() {
        return LocalDate.now().minusDays(5);
    }

    @Test
    void chineseDate_inHtmlHead_parsed() {
        LocalDate d = pastDay();
        String html = "<html><body><p>发布公告：" + d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth()
                + "日</p><p>正文...</p></body></html>";
        assertEquals(d.atStartOfDay(), NoticePublishTimeResolver.resolveFromText(html, null));
    }

    @Test
    void isoDateTime_parsed() {
        LocalDate d = pastDay();
        String html = "<p>发布时间：" + d + " 10:30:00</p>";
        assertEquals(LocalDateTime.of(d, java.time.LocalTime.of(10, 30, 0)),
                NoticePublishTimeResolver.resolveFromText(html, null));
    }

    @Test
    void slashAndDot_parsed() {
        LocalDate d = pastDay();
        assertEquals(d.atStartOfDay(), NoticePublishTimeResolver.resolveFromText(
                "<p>" + d.getYear() + "/" + d.getMonthValue() + "/" + d.getDayOfMonth() + "</p>", null));
        assertEquals(d.atStartOfDay(), NoticePublishTimeResolver.resolveFromText(
                "<p>" + d.getYear() + "." + d.getMonthValue() + "." + d.getDayOfMonth() + "</p>", null));
    }

    @Test
    void futureDeadline_skipped_fallsBackToPastDate() {
        // 正文先出现报名截止（未来时间），应跳过并取后续的发布日期
        LocalDate d = pastDay();
        String html = "<p>投标截止：2099年5月1日</p><p>发布日期：" + d.getYear() + "年" + d.getMonthValue() + "月"
                + d.getDayOfMonth() + "日</p>";
        assertEquals(d.atStartOfDay(), NoticePublishTimeResolver.resolveFromText(html, null));
    }

    @Test
    void titleFallback_usedWhenHtmlEmpty() {
        LocalDate d = pastDay();
        String title = "某工程招标公告（" + d + "）";
        assertEquals(d.atStartOfDay(), NoticePublishTimeResolver.resolveFromText(null, title));
    }

    @Test
    void noDate_returnsNull() {
        assertNull(NoticePublishTimeResolver.resolveFromText("<p>无日期正文</p>", "某工程招标公告"));
        assertNull(NoticePublishTimeResolver.resolveFromText(null, null));
    }

    @Test
    void invalidCalendar_returnsNull() {
        assertNull(NoticePublishTimeResolver.resolveFromText("<p>2026-02-30</p>", null));
    }

}
