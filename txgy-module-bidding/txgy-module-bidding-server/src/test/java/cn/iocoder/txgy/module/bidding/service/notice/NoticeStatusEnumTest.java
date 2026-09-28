package cn.iocoder.txgy.module.bidding.service.notice;

import cn.iocoder.txgy.module.bidding.enums.NoticeStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公告状态机（TDD）：可流转表见 {@link NoticeStatusEnum}。
 */
class NoticeStatusEnumTest {

    @Test
    void draft_publish_unpublish_republish_cycle() {
        assertTrue(NoticeStatusEnum.canTransit(NoticeStatusEnum.DRAFT, NoticeStatusEnum.PUBLISHED), "草稿→发布");
        assertTrue(NoticeStatusEnum.canTransit(NoticeStatusEnum.PUBLISHED, NoticeStatusEnum.UNPUBLISHED), "发布→撤下");
        assertTrue(NoticeStatusEnum.canTransit(NoticeStatusEnum.UNPUBLISHED, NoticeStatusEnum.PUBLISHED), "撤下→重发");
    }

    @Test
    void anyActiveStateCanArchive() {
        assertTrue(NoticeStatusEnum.canTransit(NoticeStatusEnum.DRAFT, NoticeStatusEnum.ARCHIVED));
        assertTrue(NoticeStatusEnum.canTransit(NoticeStatusEnum.PUBLISHED, NoticeStatusEnum.ARCHIVED));
        assertTrue(NoticeStatusEnum.canTransit(NoticeStatusEnum.UNPUBLISHED, NoticeStatusEnum.ARCHIVED));
    }

    @Test
    void illegalTransitionsRejected() {
        // 发布前的公告不允许直接撤下/归档前置乱跳
        assertFalse(NoticeStatusEnum.canTransit(NoticeStatusEnum.DRAFT, NoticeStatusEnum.UNPUBLISHED), "草稿→撤下不允许");
        // 终态不允许再动
        assertFalse(NoticeStatusEnum.canTransit(NoticeStatusEnum.ARCHIVED, NoticeStatusEnum.PUBLISHED), "归档不可再发布");
        assertFalse(NoticeStatusEnum.canTransit(NoticeStatusEnum.ARCHIVED, NoticeStatusEnum.UNPUBLISHED));
        // 同态/空态不允许
        assertFalse(NoticeStatusEnum.canTransit(NoticeStatusEnum.PUBLISHED, NoticeStatusEnum.PUBLISHED));
        assertFalse(NoticeStatusEnum.canTransit(null, NoticeStatusEnum.PUBLISHED));
    }
}
