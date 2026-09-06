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
 * 审核（NEW→ACTIVE 条件更新防丢失更新）单测（Mockito，无 Spring 上下文）。
 *
 * <p>安全评审修复：approve/reject/takeDown 由“check-then-全量 updateById”改为
 * “WHERE status/audit 条件更新 + 影响行数判定”——旧快照无法覆盖并发已推进的状态
 * （如 reject 读旧快照覆盖 approve 结果）。幂等/非法状态语义不变。
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
        // 条件更新默认命中 1 行；并发已推进场景（应判 STATE_INVALID）由用例改桩为 0
        when(noticeExternalMapper.update(any(), any())).thenReturn(1);
    }

    private NoticeExternalDO newNotice() {
        NoticeExternalDO notice = new NoticeExternalDO();
        notice.setId(100L);
        notice.setStatus(NoticeExternalStatusEnum.NEW.getStatus());
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus());
        notice.setTitle("某外部招标公告");
        return notice;
    }

    private void stubUpdatedActive() {
        NoticeExternalDO updated = newNotice();
        updated.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        updated.setAuditStatus(NoticeExternalAuditStatusEnum.APPROVED.getStatus());
        when(noticeExternalMapper.selectById(100L)).thenReturn(newNotice(), updated);
    }

    @Test
    void approveExternal_newNotice_flipsActiveAndFansOut() {
        stubUpdatedActive();

        service.approveExternal(100L);

        // 条件更新（WHERE id AND status=NEW AND audit=UNREVIEWED），非全量对象覆盖
        verify(noticeExternalMapper).update(any(NoticeExternalDO.class), any());
        verify(followService).handleNoticeActivated(any(NoticeExternalDO.class), eq("EXTERNAL"));
        verify(notifier).indexNotice(any(NoticeExternalDO.class));
    }

    @Test
    void approveExternal_alreadyActive_isIdempotent() {
        NoticeExternalDO notice = newNotice();
        notice.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.APPROVED.getStatus());
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);

        service.approveExternal(100L);

        verify(noticeExternalMapper, never()).update(any(), any());
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
        stubUpdatedActive();
        doThrow(new IllegalStateException("follow boom")).when(followService)
                .handleNoticeActivated(any(), eq("EXTERNAL"));

        service.approveExternal(100L); // 不抛：扇出/索引为旁路通知

        verify(noticeExternalMapper).update(any(NoticeExternalDO.class), any());
        verify(notifier).indexNotice(any(NoticeExternalDO.class));
    }

    @Test
    void approveExternal_secondTimeAfterApproved_noDoubleFanout() {
        // selectById 序列：① 首调存在性/幂等查（NEW）→ ② 条件更新后回读（ACTIVE，扇出）→ ③ 二次调用幂等短路（ACTIVE）
        NoticeExternalDO updated = newNotice();
        updated.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        updated.setAuditStatus(NoticeExternalAuditStatusEnum.APPROVED.getStatus());
        when(noticeExternalMapper.selectById(100L)).thenReturn(newNotice(), updated, updated);

        service.approveExternal(100L);
        service.approveExternal(100L);

        verify(noticeExternalMapper).update(any(NoticeExternalDO.class), any());
        verify(followService, times(1)).handleNoticeActivated(any(), eq("EXTERNAL"));
        verify(notifier, times(1)).indexNotice(any());
    }

    @Test
    void approveExternal_concurrentStateAdvanced_throwsStateInvalid() {
        // 并发已把记录推进（如已被 takeDown/reject）：条件更新 0 行 → 拒绝旧快照覆盖
        NoticeExternalDO notice = newNotice();
        notice.setStatus(NoticeExternalStatusEnum.INACTIVE.getStatus());
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);
        when(noticeExternalMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.approveExternal(100L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_STATE_INVALID.getCode(), ex.getCode());
        verify(noticeExternalMapper, never()).updateById(any(NoticeExternalDO.class));
    }

    @Test
    void approveExternal_rejectedNotice_throwsStateInvalid() {
        // 驳回记录不可直接 approve；恢复入口 = 同 URL 重新采集进审核（ingest 重投语义）
        NoticeExternalDO notice = newNotice();
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.REJECTED.getStatus());
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);
        when(noticeExternalMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.approveExternal(100L));
        assertEquals(ErrorCodeConstants.EXTERNAL_NOTICE_STATE_INVALID.getCode(), ex.getCode());
    }

    @Test
    void rejectExternal_newNotice_rejected() {
        when(noticeExternalMapper.selectById(100L)).thenReturn(newNotice());

        service.rejectExternal(100L);

        verify(noticeExternalMapper).update(any(NoticeExternalDO.class), any());
    }

    @Test
    void rejectExternal_alreadyRejected_isIdempotent() {
        NoticeExternalDO notice = newNotice();
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.REJECTED.getStatus());
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);

        service.rejectExternal(100L);

        verify(noticeExternalMapper, never()).update(any(), any());
    }

    @Test
    void rejectExternal_concurrentApproved_throwsStateInvalid() {
        // 并发已 approve：reject 旧快照条件更新 0 行 → STATE_INVALID，不覆盖 approve 结果（安全评审修复点）
        NoticeExternalDO notice = newNotice();
        notice.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.APPROVED.getStatus());
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);
        when(noticeExternalMapper.update(any(), any())).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.rejectExternal(100L));
        verify(noticeExternalMapper, never()).updateById(any(NoticeExternalDO.class));
    }

    @Test
    void takeDownExternal_activeNotice_inactiveAndTakenDown() {
        NoticeExternalDO notice = newNotice();
        notice.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.APPROVED.getStatus());
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);

        service.takeDownExternal(100L);

        verify(noticeExternalMapper).update(any(NoticeExternalDO.class), any());
    }

    @Test
    void takeDownExternal_newNotice_throwsStateInvalid() {
        when(noticeExternalMapper.selectById(100L)).thenReturn(newNotice());
        when(noticeExternalMapper.update(any(), any())).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.takeDownExternal(100L));
    }

    @Test
    void takeDownExternal_alreadyInactive_isIdempotent() {
        NoticeExternalDO notice = newNotice();
        notice.setStatus(NoticeExternalStatusEnum.INACTIVE.getStatus());
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.TAKEN_DOWN.getStatus());
        when(noticeExternalMapper.selectById(100L)).thenReturn(notice);

        service.takeDownExternal(100L); // 不抛

        verify(noticeExternalMapper, never()).update(any(), any());
    }

}
