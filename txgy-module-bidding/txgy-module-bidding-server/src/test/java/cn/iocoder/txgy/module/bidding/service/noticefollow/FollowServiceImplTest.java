package cn.iocoder.txgy.module.bidding.service.noticefollow;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowEventPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowEventRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.FollowItemDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.FollowItemEventDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.NoticeSubscriptionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.SubscriptionEventDO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowStatusRespVO;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticefollow.FollowItemEventMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticefollow.FollowItemMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticefollow.NoticeSubscriptionMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticefollow.SubscriptionEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关注/关注项目列表服务测试（Mockito，无 Spring 上下文）：
 * 关注幂等/退订再关注恢复、关注行建行、时间线归属校验、外部公告 ACTIVE 跨租户扇出+事件去重+IN_APP 留痕。
 */
class FollowServiceImplTest {

    private NoticeSubscriptionMapper subMapper;
    private FollowItemMapper itemMapper;
    private FollowItemEventMapper eventMapper;
    private SubscriptionEventMapper seMapper;
    private NoticeExternalMapper externalMapper;
    private NoticeMapper noticeMapper;
    private FollowServiceImpl service;

    @BeforeEach
    void setUp() {
        subMapper = mock(NoticeSubscriptionMapper.class);
        itemMapper = mock(FollowItemMapper.class);
        eventMapper = mock(FollowItemEventMapper.class);
        seMapper = mock(SubscriptionEventMapper.class);
        externalMapper = mock(NoticeExternalMapper.class);
        noticeMapper = mock(NoticeMapper.class);
        service = new FollowServiceImpl();
        ReflectionTestUtils.setField(service, "noticeSubscriptionMapper", subMapper);
        ReflectionTestUtils.setField(service, "followItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "followItemEventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "subscriptionEventMapper", seMapper);
        ReflectionTestUtils.setField(service, "noticeExternalMapper", externalMapper);
        ReflectionTestUtils.setField(service, "noticeMapper", noticeMapper);
    }

    private FollowReqVO req(String type, String key) {
        FollowReqVO req = new FollowReqVO();
        req.setTargetType(type);
        req.setTargetKey(key);
        return req;
    }

    private NoticeSubscriptionDO subscription(Long userId, Long tenantId, String type, String key) {
        NoticeSubscriptionDO sub = new NoticeSubscriptionDO();
        sub.setId(1L);
        sub.setTenantId(tenantId);
        sub.setUserId(userId);
        sub.setTargetType(type);
        sub.setTargetKey(key);
        sub.setFollowStatus("FOLLOWING");
        return sub;
    }

    private NoticeExternalDO notice() {
        NoticeExternalDO notice = new NoticeExternalDO();
        notice.setId(100L);
        notice.setTitle("某防腐保温管道工程招标公告");
        notice.setOrigUrl("https://example.com/n/100");
        notice.setProjectCode("P-1");
        notice.setTenderProjectCode("TP-1");
        notice.setBidSectionCodes("SEC-1,SEC-2");
        notice.setProjectName("防腐保温管道工程");
        notice.setIssuerName("某建设单位");
        return notice;
    }

    @Test
    void follow_firstTime_insertsSubscriptionAndItem() {
        when(subMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.selectOne(any())).thenReturn(null);

        service.follow(7L, req("SECTION", "SEC-1"));

        ArgumentCaptor<NoticeSubscriptionDO> sub = ArgumentCaptor.forClass(NoticeSubscriptionDO.class);
        verify(subMapper).insert(sub.capture());
        assertEquals(7L, sub.getValue().getUserId());
        assertEquals("SECTION", sub.getValue().getTargetType());
        assertEquals("FOLLOWING", sub.getValue().getFollowStatus());
        ArgumentCaptor<FollowItemDO> item = ArgumentCaptor.forClass(FollowItemDO.class);
        verify(itemMapper).insert(item.capture());
        assertEquals("SEC-1", item.getValue().getFollowKey());
        assertEquals("UNKNOWN", item.getValue().getStageCode());
    }

    @Test
    void follow_alreadyFollowing_isIdempotent() {
        when(subMapper.selectOne(any())).thenReturn(subscription(7L, 1L, "PROJECT", "P-1"));

        service.follow(7L, req("PROJECT", "P-1"));

        verify(subMapper, never()).insert(any(NoticeSubscriptionDO.class));
        verify(itemMapper, never()).insert(any(FollowItemDO.class));
        verify(subMapper, never()).updateById(any(NoticeSubscriptionDO.class));
    }

    @Test
    void unfollow_thenRefollow_restoresFollowing() {
        NoticeSubscriptionDO sub = subscription(7L, 1L, "PROJECT", "P-1");
        when(subMapper.selectOne(any())).thenReturn(sub);
        FollowItemDO item = new FollowItemDO();
        item.setId(5L);
        item.setUserId(7L);
        item.setFollowType("PROJECT");
        item.setFollowKey("P-1");
        item.setFollowStatus("FOLLOWING");
        when(itemMapper.selectOne(any())).thenReturn(item);

        service.unfollow(7L, req("PROJECT", "P-1"));
        assertEquals("UNFOLLOWED", sub.getFollowStatus());
        verify(subMapper).updateById(sub);
        verify(itemMapper).updateById(item);

        sub.setFollowStatus("UNFOLLOWED");
        service.follow(7L, req("PROJECT", "P-1"));
        assertEquals("FOLLOWING", sub.getFollowStatus());
        assertNull(sub.getUnfollowTime());
        verify(subMapper, times(2)).updateById(sub);
    }

    @Test
    void pageMyFollow_returnsFollowingRows() {
        FollowItemDO item = new FollowItemDO();
        item.setId(5L);
        item.setFollowType("SECTION");
        item.setFollowKey("SEC-1");
        item.setDisplayName("防腐保温管道工程");
        item.setStageCode("TENDER");
        item.setUnreadCount(2);
        Page<FollowItemDO> page = new Page<>(1, 10);
        page.setRecords(List.of(item));
        page.setTotal(1);
        when(itemMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<FollowItemRespVO> result = service.pageMyFollow(7L, new FollowItemPageReqVO());

        assertEquals(1, result.getList().size());
        assertEquals("SEC-1", result.getList().get(0).getFollowKey());
        assertEquals(2, result.getList().get(0).getUnreadCount());
    }

    @Test
    void pageTimeline_otherUsersItem_returnsEmpty() {
        FollowItemDO other = new FollowItemDO();
        other.setId(5L);
        other.setUserId(999L);
        when(itemMapper.selectById(5L)).thenReturn(other);

        PageResult<FollowEventRespVO> result = service.pageTimeline(7L, 5L, new FollowEventPageReqVO());

        assertTrue(result.getList().isEmpty());
        verify(eventMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void handleNoticeActivated_matchesSection_dedupesAndWritesInApp() {
        NoticeSubscriptionDO subSec = subscription(7L, 1L, "SECTION", "SEC-1");
        when(subMapper.selectList(any())).thenReturn(List.of(subSec));
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(eventMapper.selectOne(any())).thenReturn(null);

        service.handleNoticeActivated(notice(), "EXTERNAL");

        ArgumentCaptor<FollowItemDO> itemCap = ArgumentCaptor.forClass(FollowItemDO.class);
        verify(itemMapper).insert(itemCap.capture());
        assertEquals("SEC-1", itemCap.getValue().getFollowKey());
        ArgumentCaptor<FollowItemEventDO> eventCap = ArgumentCaptor.forClass(FollowItemEventDO.class);
        verify(eventMapper).insert(eventCap.capture());
        assertEquals(Boolean.FALSE, eventCap.getValue().getReadFlag());
        assertEquals("EXTERNAL", eventCap.getValue().getSource());
        ArgumentCaptor<SubscriptionEventDO> seCap = ArgumentCaptor.forClass(SubscriptionEventDO.class);
        verify(seMapper).insert(seCap.capture());
        assertEquals("IN_APP", seCap.getValue().getChannel());
        assertEquals("PENDING", seCap.getValue().getChannelStatus());

        // 同一事件再次触发（事件已存在）→ 不重复建事件/提醒
        when(eventMapper.selectOne(any())).thenReturn(eventCap.getValue());
        service.handleNoticeActivated(notice(), "EXTERNAL");
        verify(eventMapper, times(1)).insert(any(FollowItemEventDO.class));
        verify(seMapper, times(1)).insert(any(SubscriptionEventDO.class));
    }

    @Test
    void handleNoticeActivated_nonMatchingKey_skips() {
        NoticeSubscriptionDO subOther = subscription(8L, 2L, "SECTION", "OTHER-SEC");
        when(subMapper.selectList(any())).thenReturn(List.of(subOther));

        service.handleNoticeActivated(notice(), "EXTERNAL");

        verify(itemMapper, never()).insert(any(FollowItemDO.class));
        verify(eventMapper, never()).insert(any(FollowItemEventDO.class));
        verify(seMapper, never()).insert(any(SubscriptionEventDO.class));
    }

    @Test
    void markRead_clearsUnreadAndPatchesEvents() {
        FollowItemDO item = new FollowItemDO();
        item.setId(5L);
        item.setUserId(7L);
        item.setUnreadCount(3);
        when(itemMapper.selectById(5L)).thenReturn(item);

        service.markRead(7L, 5L);

        assertEquals(0, item.getUnreadCount());
        verify(itemMapper).updateById(item);
        verify(eventMapper).update(any(), any());
    }

    @Test
    void follow_announcement_snapshotsProjectNameAndIssuer() {
        // N2①：公告级首次关注，建订阅+行时按公告主档快照 displayName（项目名>标题）+ issuerName
        when(subMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(externalMapper.selectById(100L)).thenReturn(notice());

        service.follow(7L, req("ANNOUNCEMENT", "100"));

        ArgumentCaptor<NoticeSubscriptionDO> sub = ArgumentCaptor.forClass(NoticeSubscriptionDO.class);
        verify(subMapper).insert(sub.capture());
        assertEquals("ANNOUNCEMENT", sub.getValue().getTargetType());
        ArgumentCaptor<FollowItemDO> item = ArgumentCaptor.forClass(FollowItemDO.class);
        verify(itemMapper).insert(item.capture());
        assertEquals("防腐保温管道工程", item.getValue().getDisplayName());
        assertEquals("某建设单位", item.getValue().getIssuerName());
    }

    @Test
    void follow_invalidTargetType_rejected() {
        // 目标类型白名单：拒绝拼写错误入库
        try {
            service.follow(7L, req("WRONG", "X"));
        } catch (cn.iocoder.txgy.framework.common.exception.ServiceException expected) {
            // ignore
        }
        verify(subMapper, never()).insert(any(NoticeSubscriptionDO.class));
    }

    @Test
    void getFollowStatus_unfollowed_returnsFalse() {
        when(subMapper.selectOne(any())).thenReturn(null);

        FollowStatusRespVO resp = service.getFollowStatus(7L, "ANNOUNCEMENT", "100");

        assertEquals(Boolean.FALSE, resp.getFollowed());
        assertEquals("ANNOUNCEMENT", resp.getTargetType());
        assertEquals("100", resp.getTargetKey());
        verify(itemMapper, never()).selectOne(any());
    }


    @Test
    void getFollowStatus_followingAnnouncement_backfillsNameFromNotice() {
        // N2②：公告级已关注且行缺快照（displayName=key 雪花位），单点查询即时回查补名
        when(subMapper.selectOne(any())).thenReturn(subscription(7L, 1L, "ANNOUNCEMENT", "100"));
        FollowItemDO item = new FollowItemDO();
        item.setId(5L);
        item.setUserId(7L);
        item.setFollowType("ANNOUNCEMENT");
        item.setFollowKey("100");
        item.setDisplayName("100"); // 存量雪花 id 行（快照缺失）
        item.setFollowStatus("FOLLOWING");
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(externalMapper.selectById(100L)).thenReturn(notice());

        FollowStatusRespVO resp = service.getFollowStatus(7L, "ANNOUNCEMENT", "100");

        assertEquals(Boolean.TRUE, resp.getFollowed());
        assertEquals(5L, resp.getItemId().longValue());
        assertEquals("防腐保温管道工程", resp.getDisplayName());
        assertEquals("某建设单位", resp.getIssuerName());
    }

    @Test
    void getFollowStatus_followingSection_returnsStoredNameWithoutNoticeLookup() {
        when(subMapper.selectOne(any())).thenReturn(subscription(7L, 1L, "SECTION", "SEC-1"));
        FollowItemDO item = new FollowItemDO();
        item.setId(6L);
        item.setUserId(7L);
        item.setFollowType("SECTION");
        item.setFollowKey("SEC-1");
        item.setDisplayName("防腐保温管道工程-标段1");
        item.setFollowStatus("FOLLOWING");
        when(itemMapper.selectOne(any())).thenReturn(item);

        FollowStatusRespVO resp = service.getFollowStatus(7L, "SECTION", "SEC-1");

        assertEquals(Boolean.TRUE, resp.getFollowed());
        assertEquals("防腐保温管道工程-标段1", resp.getDisplayName());
        verify(externalMapper, never()).selectById(any());
    }

    @Test
    void pageMyFollow_followKeyFilter_backfillsMissingAnnouncementName() {
        // N2①+followKey：列表过滤 + ANNOUNCEMENT 缺快照行批量回查补名
        FollowItemDO item = new FollowItemDO();
        item.setId(5L);
        item.setFollowType("ANNOUNCEMENT");
        item.setFollowKey("100");
        item.setDisplayName("100");
        item.setFollowStatus("FOLLOWING");
        Page<FollowItemDO> page = new Page<>(1, 10);
        page.setRecords(List.of(item));
        page.setTotal(1);
        when(itemMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(externalMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(notice()));

        FollowItemPageReqVO reqVO = new FollowItemPageReqVO();
        reqVO.setFollowType("ANNOUNCEMENT");
        reqVO.setFollowKey("100");
        PageResult<FollowItemRespVO> result = service.pageMyFollow(7L, reqVO);

        assertEquals(1, result.getList().size());
        assertEquals("防腐保温管道工程", result.getList().get(0).getDisplayName());
        assertEquals("某建设单位", result.getList().get(0).getIssuerName());
    }

    @Test
    void follow_restoreAfterUnfollow_refreshesAnnouncementSnapshot() {
        // 退订再关注：恢复行并刷新公告快照（N2①：重关后标题应为最新）
        NoticeSubscriptionDO sub = subscription(7L, 1L, "ANNOUNCEMENT", "100");
        when(subMapper.selectOne(any())).thenReturn(sub);
        FollowItemDO item = new FollowItemDO();
        item.setId(5L);
        item.setUserId(7L);
        item.setFollowType("ANNOUNCEMENT");
        item.setFollowKey("100");
        item.setDisplayName("100");
        item.setFollowStatus("FOLLOWING");
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(externalMapper.selectById(100L)).thenReturn(notice());

        // 退订
        service.unfollow(7L, req("ANNOUNCEMENT", "100"));
        assertEquals("UNFOLLOWED", sub.getFollowStatus());
        assertEquals("UNFOLLOWED", item.getFollowStatus());
        // 重关（快照刷新：displayName 从雪花位 → 项目名）
        service.follow(7L, req("ANNOUNCEMENT", "100"));
        assertEquals("FOLLOWING", item.getFollowStatus());
        assertEquals("防腐保温管道工程", item.getDisplayName());
        assertEquals("某建设单位", item.getIssuerName());
    }

    // ===== BR-6 关注已生效公告的初始反馈（seed 已读初始动态）=====
    @Test
    void follow_activeAnnouncement_seedsReadInitialEvent() {
        // BR-6：关注 ACTIVE 公告 → 种一条已读 EXTERNAL_ACTIVE 初始动态并刷新最近动态时间（timeline 非空、未读不增）
        when(subMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.selectOne(any())).thenReturn(null);
        NoticeExternalDO active = notice();
        active.setStatus("ACTIVE");
        when(externalMapper.selectById(100L)).thenReturn(active);
        doAnswer(inv -> {
            ((FollowItemDO) inv.getArgument(0)).setId(9L);
            return 1;
        }).when(itemMapper).insert(any(FollowItemDO.class));

        service.follow(7L, req("ANNOUNCEMENT", "100"));

        ArgumentCaptor<FollowItemEventDO> event = ArgumentCaptor.forClass(FollowItemEventDO.class);
        verify(eventMapper).insert(event.capture());
        assertEquals(Boolean.TRUE, event.getValue().getReadFlag()); // 初始动态已读，不制造未读提醒
        assertEquals("EXTERNAL", event.getValue().getSource());
        assertEquals("EXTERNAL_ACTIVE", event.getValue().getEventType());
        assertEquals(100L, event.getValue().getNoticeId().longValue());
        assertTrue(event.getValue().getEventKey().endsWith("-seed"));
        ArgumentCaptor<FollowItemDO> updated = ArgumentCaptor.forClass(FollowItemDO.class);
        verify(itemMapper).updateById(updated.capture());
        assertEquals(0, updated.getValue().getUnreadCount().intValue()); // 未读角标不增加
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getValue().getLatestDynamicTime());
    }

    @Test
    void follow_notActiveAnnouncement_noSeedEvent() {
        // BR-6：关注未生效（NEW）公告不种初始动态（等审核生效扇出再提醒）
        when(subMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.selectOne(any())).thenReturn(null);
        NoticeExternalDO pending = notice();
        pending.setStatus("NEW");
        when(externalMapper.selectById(100L)).thenReturn(pending);

        service.follow(7L, req("ANNOUNCEMENT", "100"));

        verify(eventMapper, never()).insert(any(FollowItemEventDO.class));
    }

    // ===== BR-4 关注行/事件可达性状态 =====
    @Test
    void pageMyFollow_announcementRow_exposesNoticeAvailability() {
        FollowItemDO row = new FollowItemDO();
        row.setId(5L);
        row.setUserId(7L);
        row.setFollowType("ANNOUNCEMENT");
        row.setFollowKey("100");
        row.setFollowStatus("FOLLOWING");
        Page<FollowItemDO> page = Page.of(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(itemMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        NoticeExternalDO active = notice();
        active.setStatus("ACTIVE");
        when(externalMapper.selectBatchIds(anyList())).thenReturn(List.of(active));

        PageResult<FollowItemRespVO> result = service.pageMyFollow(7L, new FollowItemPageReqVO());

        assertEquals(1, result.getList().size());
        FollowItemRespVO vo = result.getList().get(0);
        assertEquals("ACTIVE", vo.getNoticeStatus());
        assertEquals(Boolean.TRUE, vo.getAvailable());
    }

    @Test
    void pageTimeline_events_carryNoticeAvailability() {
        FollowItemDO item = new FollowItemDO();
        item.setId(9L);
        item.setUserId(7L);
        item.setFollowType("SECTION");
        item.setFollowKey("SEC-1");
        item.setFollowStatus("FOLLOWING");
        when(itemMapper.selectById(9L)).thenReturn(item);

        FollowItemEventDO ext = new FollowItemEventDO();
        ext.setId(1L);
        ext.setUserId(7L);
        ext.setItemId(9L);
        ext.setSource("EXTERNAL");
        ext.setEventType("EXTERNAL_ACTIVE");
        ext.setNoticeId(100L);
        FollowItemEventDO intAct = new FollowItemEventDO();
        intAct.setId(2L);
        intAct.setUserId(7L);
        intAct.setItemId(9L);
        intAct.setSource("INTERNAL");
        intAct.setEventType("NOTICE_PUBLISHED");
        intAct.setNoticeId(55L);
        Page<FollowItemEventDO> page = Page.of(1, 10);
        page.setRecords(List.of(ext, intAct));
        page.setTotal(2);
        when(eventMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        // EXTERNAL 公告已下线（INACTIVE）；INTERNAL 公告已发布（PUBLISHED）
        NoticeExternalDO inactive = notice();
        inactive.setStatus("INACTIVE");
        when(externalMapper.selectBatchIds(anyList())).thenReturn(List.of(inactive));
        NoticeDO published = new NoticeDO();
        published.setId(55L);
        published.setStatus("PUBLISHED");
        when(noticeMapper.selectBatchIds(anyList())).thenReturn(List.of(published));

        PageResult<FollowEventRespVO> result = service.pageTimeline(7L, 9L, new FollowEventPageReqVO());

        assertEquals(2, result.getList().size());
        FollowEventRespVO extVo = result.getList().get(0);
        assertEquals("INACTIVE", extVo.getNoticeStatus());
        assertEquals(Boolean.FALSE, extVo.getAvailable()); // 已下线 → 隐藏平台详情跳转（前端按源站原文兜底）
        FollowEventRespVO intVo = result.getList().get(1);
        assertEquals("PUBLISHED", intVo.getNoticeStatus());
        assertEquals(Boolean.TRUE, intVo.getAvailable());
    }

    // ===== 内部公告订阅闭环（业务核查补全 2026-09-07）：发布/更新扇出、版本幂等、双源行可达 =====

    @Test
    void handleInternalPublished_fansOutToAnnouncementAndProjectSubscribers() {
        NoticeSubscriptionDO subAnn = subscription(7L, 1L, "ANNOUNCEMENT", "55");
        NoticeSubscriptionDO subProj = subscription(8L, 1L, "PROJECT", "P-1");
        subProj.setId(2L); // 订阅 id 需唯一（扇出去重按订阅 id）
        when(subMapper.selectList(any())).thenReturn(List.of(subAnn), List.of(subProj));
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(eventMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            ((FollowItemDO) inv.getArgument(0)).setId(101L);
            return 1;
        }).when(itemMapper).insert(any(FollowItemDO.class));

        service.handleInternalNoticeChanged("PUBLISHED", 55L, "某集团2026年防腐管道招标", "某集团", "防腐管道项目", "P-1", null, java.util.List.of(), 1);

        // 公告级 + 项目级订阅各收到一条未读动态（IN_APP 提醒留痕）
        ArgumentCaptor<FollowItemEventDO> event = ArgumentCaptor.forClass(FollowItemEventDO.class);
        verify(eventMapper, times(2)).insert(event.capture());
        for (FollowItemEventDO e : event.getAllValues()) {
            assertEquals("INTERNAL", e.getSource());
            assertEquals("NOTICE_PUBLISHED", e.getEventType());
            assertEquals(55L, e.getNoticeId().longValue());
            assertEquals(Boolean.FALSE, e.getReadFlag());
            assertTrue(e.getEventKey().endsWith("-55-NOTICE_PUBLISHED-1"), "事件键须含来源+公告+动作+版本");
            assertEquals("某集团2026年防腐管道招标", e.getTitleSnapshot());
        }
        // 行快照：公告级=公告标题，项目级=项目名
        ArgumentCaptor<FollowItemDO> item = ArgumentCaptor.forClass(FollowItemDO.class);
        verify(itemMapper, times(2)).insert(item.capture());
        assertTrue(item.getAllValues().stream().anyMatch(i ->
                "ANNOUNCEMENT".equals(i.getFollowType()) && "某集团2026年防腐管道招标".equals(i.getDisplayName())));
        assertTrue(item.getAllValues().stream().anyMatch(i ->
                "PROJECT".equals(i.getFollowType()) && "防腐管道项目".equals(i.getDisplayName())));
        // 未读+1、最近动态时间刷新、IN_APP PENDING 留痕
        ArgumentCaptor<FollowItemDO> updated = ArgumentCaptor.forClass(FollowItemDO.class);
        verify(itemMapper, times(2)).updateById(updated.capture());
        assertTrue(updated.getAllValues().stream().allMatch(i -> Integer.valueOf(1).equals(i.getUnreadCount())));
        assertTrue(updated.getAllValues().stream().allMatch(i -> i.getLatestDynamicTime() != null));
        ArgumentCaptor<SubscriptionEventDO> se = ArgumentCaptor.forClass(SubscriptionEventDO.class);
        verify(seMapper, times(2)).insert(se.capture());
        assertTrue(se.getAllValues().stream().allMatch(s ->
                "IN_APP".equals(s.getChannel()) && "PENDING".equals(s.getChannelStatus())));
    }

    @Test
    void handleInternalUpdated_sameVersionIdempotent_newVersionFansOutAgain() {
        NoticeSubscriptionDO subAnn = subscription(7L, 1L, "ANNOUNCEMENT", "55");
        when(subMapper.selectList(any())).thenReturn(List.of(subAnn));
        FollowItemDO item = new FollowItemDO();
        item.setId(5L);
        item.setUserId(7L);
        item.setFollowType("ANNOUNCEMENT");
        item.setFollowKey("55");
        item.setDisplayName("旧标题");
        item.setUnreadCount(0);
        item.setFollowStatus("FOLLOWING");
        when(itemMapper.selectOne(any())).thenReturn(item);
        // 三次 selectOne：v2 首次(无事件) / v2 重放(已存在) / v3(新版本无事件)
        FollowItemEventDO exists = new FollowItemEventDO();
        exists.setId(901L);
        when(eventMapper.selectOne(any())).thenReturn(null, exists, null);

        service.handleInternalNoticeChanged("UPDATED", 55L, "标题v2", "某集团", null, null, null, java.util.List.of(), 2);
        service.handleInternalNoticeChanged("UPDATED", 55L, "标题v2", "某集团", null, null, null, java.util.List.of(), 2); // 同版本重放：幂等
        service.handleInternalNoticeChanged("UPDATED", 55L, "标题v3", "某集团", null, null, null, java.util.List.of(), 3); // 新版本：再提醒

        ArgumentCaptor<FollowItemEventDO> event = ArgumentCaptor.forClass(FollowItemEventDO.class);
        verify(eventMapper, times(2)).insert(event.capture());
        assertEquals("NOTICE_UPDATED", event.getAllValues().get(0).getEventType());
        assertTrue(event.getAllValues().get(0).getEventKey().endsWith("-55-NOTICE_UPDATED-2"));
        assertTrue(event.getAllValues().get(1).getEventKey().endsWith("-55-NOTICE_UPDATED-3"));
        verify(seMapper, times(2)).insert(any(SubscriptionEventDO.class));
        // 未读两次递增（重放不递增），行快照标题随最新版本刷新
        verify(itemMapper, times(2)).updateById(any(FollowItemDO.class));
        assertEquals(2, item.getUnreadCount().intValue());
        assertEquals("标题v3", item.getDisplayName());
    }

    // ===== 契约 B2：维度关注回填本站公告（2026-09-23）=====
    // 背景：原实现只回填外部公告，用户关注一个项目后，「投标流程管理」里看不到该项目此前
    // 发布过的**本站**公告——同一套 UI 两种能力。以下用例覆盖三级维度键与"已读不轰炸"语义。

    /** 造一条 PUBLISHED 本站公告（三级编号齐备）。 */
    private NoticeDO publishedInternal(Long id, String projectCode, String tenderProjectCode, String sectionCodes) {
        NoticeDO n = new NoticeDO();
        n.setId(id);
        n.setTitle("某集团内部招标公告-" + id);
        n.setStatus("PUBLISHED");
        n.setProjectCode(projectCode);
        n.setPublishTime(java.time.LocalDateTime.of(2026, 9, 20, 10, 0));
        String json = "{\"TENDER_PROJECT_CODE\":\"" + (tenderProjectCode == null ? "" : tenderProjectCode)
                + "\",\"BID_SECTION_CODES\":\"" + (sectionCodes == null ? "" : sectionCodes) + "\"}";
        n.setFieldsJson(json);
        return n;
    }

    /** 维度关注建行的公共桩：订阅不存在、关注行不存在、插入后回填 id。 */
    private void stubDimensionFollow() {
        when(subMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            ((FollowItemDO) inv.getArgument(0)).setId(9L);
            return 1;
        }).when(itemMapper).insert(any(FollowItemDO.class));
    }

    @Test
    void follow_tenderProjectDimension_backfillsInternalNotices() {
        // B2：按招标项目编号关注 → 回填该项目下已发布的本站公告（此前只回填外部公告）
        stubDimensionFollow();
        when(externalMapper.selectList(any())).thenReturn(java.util.List.of()); // 外部公告无命中
        NoticeDO hit = publishedInternal(200L, "P-1", "TP-1", "S-1,S-2");
        // 只回一条：matchesInternalFollowKey 的 TENDER_PROJECT 分支命中
        when(noticeMapper.selectList(any())).thenReturn(java.util.List.of(hit));

        service.follow(7L, req("TENDER_PROJECT", "TP-1"));

        ArgumentCaptor<FollowItemEventDO> event = ArgumentCaptor.forClass(FollowItemEventDO.class);
        verify(eventMapper).insert(event.capture());
        assertEquals("INTERNAL", event.getValue().getSource());
        assertEquals("NOTICE_PUBLISHED", event.getValue().getEventType());
        assertEquals(200L, event.getValue().getNoticeId().longValue());
        assertEquals(Boolean.TRUE, event.getValue().getReadFlag()); // 回填不制造未读轰炸
        assertTrue(event.getValue().getEventKey().endsWith("-seed"));
        // occurredAt 取公告发布时间（内容时间），不是回填时刻
        assertEquals(java.time.LocalDateTime.of(2026, 9, 20, 10, 0), event.getValue().getOccurredAt());
    }

    @Test
    void follow_sectionDimension_backfillsInternalNotices_multiCodes() {
        // B2：标段维度按 BID_SECTION_CODES 的**任一分量**命中（多标段分隔串）
        stubDimensionFollow();
        when(externalMapper.selectList(any())).thenReturn(java.util.List.of());
        NoticeDO hit = publishedInternal(201L, "P-1", "TP-1", "S-1；S-2"); // 全角分号
        when(noticeMapper.selectList(any())).thenReturn(java.util.List.of(hit));

        service.follow(7L, req("SECTION", "S-2")); // 命中第二个分量

        ArgumentCaptor<FollowItemEventDO> event = ArgumentCaptor.forClass(FollowItemEventDO.class);
        verify(eventMapper).insert(event.capture());
        assertEquals(201L, event.getValue().getNoticeId().longValue());
    }

    @Test
    void follow_projectDimension_doesNotBackfillUnrelatedNotice() {
        // B2：维度键不匹配的公告不得回填（口径漂移防护：查询粗筛后仍按精确键过滤）
        stubDimensionFollow();
        when(externalMapper.selectList(any())).thenReturn(java.util.List.of());
        NoticeDO unrelated = publishedInternal(202L, "OTHER-PRJ", "OTHER-TP", "OTHER-SEC");
        when(noticeMapper.selectList(any())).thenReturn(java.util.List.of(unrelated));

        service.follow(7L, req("PROJECT", "P-1"));

        // 键不匹配 → 不产生 INTERNAL 事件
        verify(eventMapper, never()).insert(any(FollowItemEventDO.class));
    }

    @Test
    void follow_internalPublishedAnnouncement_seedsReadInitialEvent() {
        // 业务对称：内部公告已发布（PUBLISHED）时关注 → 种已读 NOTICE_PUBLISHED 初始动态（timeline 非空、未读不增）
        when(subMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(externalMapper.selectById(100L)).thenReturn(null); // G 轨未命中
        NoticeDO internal = new NoticeDO();
        internal.setId(100L);
        internal.setTitle("某集团内部招标公告");
        internal.setStatus("PUBLISHED");
        internal.setPublisherName("某集团");
        when(noticeMapper.selectById(100L)).thenReturn(internal);
        doAnswer(inv -> {
            ((FollowItemDO) inv.getArgument(0)).setId(9L);
            return 1;
        }).when(itemMapper).insert(any(FollowItemDO.class));

        service.follow(7L, req("ANNOUNCEMENT", "100"));

        // 建行快照：内部主档标题/发布单位
        ArgumentCaptor<FollowItemDO> inserted = ArgumentCaptor.forClass(FollowItemDO.class);
        verify(itemMapper).insert(inserted.capture());
        assertEquals("某集团内部招标公告", inserted.getValue().getDisplayName());
        assertEquals("某集团", inserted.getValue().getIssuerName());
        // 已读初始动态
        ArgumentCaptor<FollowItemEventDO> event = ArgumentCaptor.forClass(FollowItemEventDO.class);
        verify(eventMapper).insert(event.capture());
        assertEquals(Boolean.TRUE, event.getValue().getReadFlag());
        assertEquals("INTERNAL", event.getValue().getSource());
        assertEquals("NOTICE_PUBLISHED", event.getValue().getEventType());
        assertEquals(100L, event.getValue().getNoticeId().longValue());
        assertEquals("某集团内部招标公告", event.getValue().getTitleSnapshot());
        assertTrue(event.getValue().getEventKey().endsWith("-seed"));
        ArgumentCaptor<FollowItemDO> updated = ArgumentCaptor.forClass(FollowItemDO.class);
        verify(itemMapper).updateById(updated.capture());
        assertEquals(0, updated.getValue().getUnreadCount().intValue()); // 未读角标不增加
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getValue().getLatestDynamicTime());
    }

    @Test
    void follow_notPublishedInternalAnnouncement_noSeedEvent() {
        // 内部公告未发布（DRAFT/UNPUBLISHED）不种初始动态（等发布扇出再提醒）
        when(subMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(externalMapper.selectById(100L)).thenReturn(null);
        NoticeDO internal = new NoticeDO();
        internal.setId(100L);
        internal.setTitle("草稿中的内部公告");
        internal.setStatus("DRAFT");
        when(noticeMapper.selectById(100L)).thenReturn(internal);

        service.follow(7L, req("ANNOUNCEMENT", "100"));

        verify(eventMapper, never()).insert(any(FollowItemEventDO.class));
    }

    @Test
    void pageMyFollow_internalAnnouncementRow_exposesPublishedStatusAndAvailability() {
        // 公告级行外部未命中 → 内部主档补名并暴露可达性（INTERNAL=PUBLISHED 可跳详情）
        FollowItemDO row = new FollowItemDO();
        row.setId(7L);
        row.setUserId(9L);
        row.setFollowType("ANNOUNCEMENT");
        row.setFollowKey("55");
        row.setDisplayName("55"); // 存量行缺快照
        row.setFollowStatus("FOLLOWING");
        Page<FollowItemDO> page = Page.of(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(itemMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(externalMapper.selectBatchIds(anyList())).thenReturn(List.of());
        NoticeDO published = new NoticeDO();
        published.setId(55L);
        published.setStatus("PUBLISHED");
        published.setTitle("某集团内部招标公告");
        published.setPublisherName("某集团");
        when(noticeMapper.selectBatchIds(anyList())).thenReturn(List.of(published));

        PageResult<FollowItemRespVO> result = service.pageMyFollow(9L, new FollowItemPageReqVO());

        assertEquals(1, result.getList().size());
        FollowItemRespVO vo = result.getList().get(0);
        assertEquals("PUBLISHED", vo.getNoticeStatus());
        assertEquals(Boolean.TRUE, vo.getAvailable());
        assertEquals("某集团内部招标公告", vo.getDisplayName());
        assertEquals("某集团", vo.getIssuerName());
    }

    @Test
    void getFollowStatus_followingInternalAnnouncement_backfillsNameFromInternalNotice() {
        // 单点状态：外部公告未命中 → 内部公告主档补名
        when(subMapper.selectOne(any())).thenReturn(subscription(7L, 1L, "ANNOUNCEMENT", "200"));
        FollowItemDO item = new FollowItemDO();
        item.setId(5L);
        item.setUserId(7L);
        item.setFollowType("ANNOUNCEMENT");
        item.setFollowKey("200");
        item.setDisplayName("200");
        item.setFollowStatus("FOLLOWING");
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(externalMapper.selectById(200L)).thenReturn(null);
        NoticeDO internal = new NoticeDO();
        internal.setId(200L);
        internal.setTitle("某内部公告");
        internal.setStatus("PUBLISHED");
        internal.setPublisherName("某集团");
        when(noticeMapper.selectById(200L)).thenReturn(internal);

        FollowStatusRespVO resp = service.getFollowStatus(7L, "ANNOUNCEMENT", "200");

        assertEquals(Boolean.TRUE, resp.getFollowed());
        assertEquals("某内部公告", resp.getDisplayName());
        assertEquals("某集团", resp.getIssuerName());
    }

}