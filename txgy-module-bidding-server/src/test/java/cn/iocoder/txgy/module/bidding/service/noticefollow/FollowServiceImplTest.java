package cn.iocoder.txgy.module.bidding.service.noticefollow;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowEventPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowEventRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.FollowItemDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.FollowItemEventDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.NoticeSubscriptionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.SubscriptionEventDO;
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
    private FollowServiceImpl service;

    @BeforeEach
    void setUp() {
        subMapper = mock(NoticeSubscriptionMapper.class);
        itemMapper = mock(FollowItemMapper.class);
        eventMapper = mock(FollowItemEventMapper.class);
        seMapper = mock(SubscriptionEventMapper.class);
        service = new FollowServiceImpl();
        ReflectionTestUtils.setField(service, "noticeSubscriptionMapper", subMapper);
        ReflectionTestUtils.setField(service, "followItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "followItemEventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "subscriptionEventMapper", seMapper);
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

}
