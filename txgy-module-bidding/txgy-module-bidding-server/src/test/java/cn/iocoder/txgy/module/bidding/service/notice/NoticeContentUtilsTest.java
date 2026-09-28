package cn.iocoder.txgy.module.bidding.service.notice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公告正文版本判定（TDD）：内容变化才追加新版本（规范第五章第三节第 2 款）。
 * 口径：null 与空串等价；除首尾空白以外的任何差异都算实质变更（含尾随换行差异）。
 */
class NoticeContentUtilsTest {

    @Test
    void appendWhenContentChanged() {
        assertTrue(NoticeContentUtils.shouldAppendVersion(null, "第一条正文"), "无旧文→有正文应建 v1");
        assertTrue(NoticeContentUtils.shouldAppendVersion("旧正文", "新正文"), "正文变了应追加 v2");
        assertTrue(NoticeContentUtils.shouldAppendVersion("正文", null), "清空正文也是实质变更");
    }

    @Test
    void noAppendWhenSameContent() {
        assertFalse(NoticeContentUtils.shouldAppendVersion(null, null), "空对空不变更");
        assertFalse(NoticeContentUtils.shouldAppendVersion(null, ""), "无旧文、新文空白不算实质变更");
        assertFalse(NoticeContentUtils.shouldAppendVersion("正文", "正文"), "完全一致不变更");
        assertFalse(NoticeContentUtils.shouldAppendVersion(" 正文 ", "正文"), "仅首尾空白差异不变更");
        assertFalse(NoticeContentUtils.shouldAppendVersion("旧正文", "旧正文\n"), "仅尾随换行差异不变更");
    }
}
