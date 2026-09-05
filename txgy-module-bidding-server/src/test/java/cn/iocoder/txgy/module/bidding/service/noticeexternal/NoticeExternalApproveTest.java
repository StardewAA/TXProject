package cn.iocoder.txgy.module.bidding.service.noticeexternal;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalAttachmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalAuditStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer.ExternalNoticeIndexNotifier;
import cn.iocoder.txgy.module.bidding.service.noticefollow.FollowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审核通过（NEW→ACTIVE→扇出关注+ES 索引事件预留）单测（Mockito，无 Spring 上下文）。
 */
class NoticeExternalApproveTest {

    private NoticeExternalMapper noticeExternalMapper;
    private FollowService followService;
    private ExternalNoticeIndexNotifier notifier;
    private NoticeExternalServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeExternalMapper = mock(NoticeExternalMapper.class);
        followService = mock(FollowService.class);
        notifier = mock(ExternalNoticeIndexNotifier.class);
        service = new NoticeExternalServiceImpl();
        ReflectionTestUtils.setField(service, "noticeExternalMapper", noticeExternalMapper);
        ReflectionTestUtils.setField(service, "noticeExternalAttachmentMapper",
                mock(NoticeExternalAttachmentMapper.class));
        ReflectionTestUtils.setField(service, "followService", followService);
        ReflectionTestUtils.setField(service, "externalNoticeIndexNotifier", notifier);
    }

    private NoticeExternalDO newNotice() {
        NoticeExternalDO notice = new NoticeExternalDO();
        notice.setId(100L);
        notice.setStatus(NoticeExternalStatusEnum.NEW.getStatus());
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus());
        notice.setTitle("某外部招标公告");
        return notice;
    }

    @Test
    void approveExternal_newNotice_flipsActiveAndFansOut() {
        NoticeExternalDO notice = newNotice();
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);

        service.approveExternal(100L);

        assertEquals(NoticeExternalStatusEnum.ACTIVE.getStatus(), notice.getStatus());
        assertEquals(NoticeExternalAuditStatusEnum.APPROVED.getStatus(), notice.getAuditStatus());
        verify(noticeExternalMapper).updateById(notice);
        verify(followService).handleNoticeActivated(notice, "EXTERNAL");
        verify(notifier).indexNotice(notice);
    }

    @Test
    void approveExternal_alreadyActive_isIdempotent() {
        NoticeExternalDO notice = newNotice();
        notice.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.APPROVED.getStatus());
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);

        service.approveExternal(100L);

        verify(noticeExternalMapper, never()).updateById(any(NoticeExternalDO.class));
        verify(followService, never()).handleNoticeActivated(any(), any());
    }

    @Test
    void approveExternal_notExists_throws() {
        when(noticeExternalMapper.selectById(100L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.approveExternal(100L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void approveExternal_fanoutFailure_doesNotRollbackAudit() {
        NoticeExternalDO notice = newNotice();
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);
        doThrow(new IllegalStateException("follow boom")).when(followService)
                .handleNoticeActivated(any(), eq("EXTERNAL"));

        service.approveExternal(100L); // 不抛

        assertEquals(NoticeExternalStatusEnum.ACTIVE.getStatus(), notice.getStatus());
        verify(noticeExternalMapper).updateById(notice);
        verify(notifier).indexNotice(notice);
    }

    @Test
    void approveExternal_secondTimeAfterApproved_noDoubleFanout() {
        NoticeExternalDO notice = newNotice();
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);

        service.approveExternal(100L);
        notice.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.APPROVED.getStatus());
        service.approveExternal(100L);

        verify(followService, times(1)).handleNoticeActivated(any(), eq("EXTERNAL"));
        verify(notifier, times(1)).indexNotice(notice);
    }

}
