package cn.iocoder.txgy.module.bidding.service.noticefollow;
// 规范依据：设计文档 v0.6 §12/§13/§15.1（T 轨拦截器隔离、事件幂等去重、IN_APP 留痕 API 占位）；
// 《招投标模块开发规范》§4.2/§4.5

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 关注/关注项目列表 服务实现。
 */
@Service
public class FollowServiceImpl implements FollowService {

    /** 关注状态 */
    private static final String STATUS_FOLLOWING = "FOLLOWING";
    private static final String STATUS_UNFOLLOWED = "UNFOLLOWED";
    /** 是否维度类型（对应关注项目列表行）；公告关注只进订阅表 */
    private static final String TYPE_ANNOUNCEMENT = "ANNOUNCEMENT";
    /** 事件/提醒相关常量 */
    private static final String EVENT_TYPE_NOTICE_PUBLISHED = "NOTICE_PUBLISHED";
    private static final String EVENT_SOURCE_EXTERNAL = "EXTERNAL";
    private static final String EVENT_SOURCE_INTERNAL = "INTERNAL";
    private static final String CHANNEL_IN_APP = "IN_APP";

    @Resource
    private NoticeSubscriptionMapper noticeSubscriptionMapper;
    @Resource
    private FollowItemMapper followItemMapper;
    @Resource
    private FollowItemEventMapper followItemEventMapper;
    @Resource
    private SubscriptionEventMapper subscriptionEventMapper;

    @Override
    public void follow(Long userId, FollowReqVO reqVO) {
        NoticeSubscriptionDO exists = findSubscription(userId, reqVO.getTargetType(), reqVO.getTargetKey());
        if (exists != null) {
            if (!STATUS_FOLLOWING.equals(exists.getFollowStatus())) {
                exists.setFollowStatus(STATUS_FOLLOWING);
                exists.setFollowTime(LocalDateTime.now());
                exists.setUnfollowTime(null);
                noticeSubscriptionMapper.updateById(exists);
                restoreFollowItem(exists);
            }
            return; // 已关注：幂等
        }
        NoticeSubscriptionDO subscription = new NoticeSubscriptionDO();
        subscription.setUserId(userId);
        subscription.setTargetType(reqVO.getTargetType());
        subscription.setTargetKey(reqVO.getTargetKey());
        subscription.setFollowStatus(STATUS_FOLLOWING);
        subscription.setFollowTime(LocalDateTime.now());
        noticeSubscriptionMapper.insert(subscription);
        // 维度关注 → 关注项目列表行
        if (!TYPE_ANNOUNCEMENT.equals(reqVO.getTargetType())) {
            FollowItemDO item = findItem(userId, reqVO.getTargetType(), reqVO.getTargetKey());
            if (item == null) {
                item = new FollowItemDO();
                item.setUserId(userId);
                item.setFollowType(reqVO.getTargetType());
                item.setFollowKey(reqVO.getTargetKey());
                item.setDisplayName(reqVO.getTargetKey());
                item.setStageCode("UNKNOWN");
                item.setUnreadCount(0);
                item.setFollowStatus(STATUS_FOLLOWING);
                item.setFollowTime(LocalDateTime.now());
                followItemMapper.insert(item);
            } else if (!STATUS_FOLLOWING.equals(item.getFollowStatus())) {
                restoreItem(item);
            }
        }
    }

    @Override
    public void unfollow(Long userId, FollowReqVO reqVO) {
        NoticeSubscriptionDO exists = findSubscription(userId, reqVO.getTargetType(), reqVO.getTargetKey());
        if (exists == null || !STATUS_FOLLOWING.equals(exists.getFollowStatus())) {
            return; // 幂等
        }
        exists.setFollowStatus(STATUS_UNFOLLOWED);
        exists.setUnfollowTime(LocalDateTime.now());
        noticeSubscriptionMapper.updateById(exists);
        if (!TYPE_ANNOUNCEMENT.equals(reqVO.getTargetType())) {
            FollowItemDO item = findItem(userId, reqVO.getTargetType(), reqVO.getTargetKey());
            if (item != null) {
                item.setFollowStatus(STATUS_UNFOLLOWED);
                item.setUnfollowTime(LocalDateTime.now());
                followItemMapper.updateById(item);
            }
        }
    }

    @Override
    public PageResult<FollowItemRespVO> pageMyFollow(Long userId, FollowItemPageReqVO reqVO) {
        LambdaQueryWrapper<FollowItemDO> wrapper = new LambdaQueryWrapper<FollowItemDO>()
                .eq(FollowItemDO::getUserId, userId)
                .eq(FollowItemDO::getFollowStatus, STATUS_FOLLOWING)
                .orderByDesc(FollowItemDO::getLatestDynamicTime);
        if (StringUtils.hasText(reqVO.getFollowType())) {
            wrapper.eq(FollowItemDO::getFollowType, reqVO.getFollowType());
        }
        IPage<FollowItemDO> page = followItemMapper.selectPage(Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toItemResp).toList(), page.getTotal());
    }

    @Override
    public PageResult<FollowEventRespVO> pageTimeline(Long userId, Long itemId, FollowEventPageReqVO reqVO) {
        // 校验归属（T 轨：租户由拦截器兜底，user 归属在此显式校验）
        FollowItemDO item = followItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getUserId(), userId)) {
            return new PageResult<>(List.of(), 0L);
        }
        LambdaQueryWrapper<FollowItemEventDO> wrapper = new LambdaQueryWrapper<FollowItemEventDO>()
                .eq(FollowItemEventDO::getItemId, itemId)
                .eq(FollowItemEventDO::getUserId, userId)
                .orderByDesc(FollowItemEventDO::getOccurredAt);
        IPage<FollowItemEventDO> page = followItemEventMapper.selectPage(Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toEventResp).toList(), page.getTotal());
    }

    @Override
    public void markRead(Long userId, Long itemId) {
        FollowItemDO item = followItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getUserId(), userId)) {
            return;
        }
        FollowItemEventDO patch = new FollowItemEventDO();
        patch.setReadFlag(Boolean.TRUE);
        followItemEventMapper.update(patch, new LambdaQueryWrapper<FollowItemEventDO>()
                .eq(FollowItemEventDO::getItemId, itemId)
                .eq(FollowItemEventDO::getUserId, userId)
                .eq(FollowItemEventDO::getReadFlag, Boolean.FALSE));
        item.setUnreadCount(0);
        followItemMapper.updateById(item);
    }

    @Override
    public void handleNoticeActivated(NoticeExternalDO notice, String source) {
        if (notice == null) {
            return;
        }
        // 平台侧触发（无登录租户）：跨租户读取全部关注（拦截器忽略），再按订阅者租户逐个写入
        List<NoticeSubscriptionDO> subscriptions = TenantUtils.executeIgnore(() ->
                noticeSubscriptionMapper.selectList(new LambdaQueryWrapper<NoticeSubscriptionDO>()
                        .eq(NoticeSubscriptionDO::getFollowStatus, STATUS_FOLLOWING)));
        for (NoticeSubscriptionDO subscription : subscriptions) {
            if (matches(subscription, notice)) {
                TenantUtils.execute(subscription.getTenantId(), () -> handleForSubscriber(subscription, notice, source));
            }
        }
    }

    /** 在订阅者租户上下文中执行：建关注行/事件去重/刷行/提醒留痕 */
    private void handleForSubscriber(NoticeSubscriptionDO subscription, NoticeExternalDO notice, String source) {
        // 事件去重：同一 (user,item,source,notice) 只写一条（event_key 幂等）
        FollowItemDO item = findItem(subscription.getUserId(), subscription.getTargetType(), subscription.getTargetKey());
        if (item == null) {
            if (TYPE_ANNOUNCEMENT.equals(subscription.getTargetType())) {
                return; // 公告级关注：暂无关注项目行，事件不落（后续公告归属维度时再提醒）
            }
            item = newItem(subscription.getUserId(), subscription.getTargetType(), subscription.getTargetKey(), notice);
            followItemMapper.insert(item);
        }
        String eventKey = eventKey(subscription.getUserId(), item.getId(), source, notice.getId());
        FollowItemEventDO exists = followItemEventMapper.selectOne(new LambdaQueryWrapper<FollowItemEventDO>()
                .eq(FollowItemEventDO::getEventKey, eventKey));
        if (exists != null) {
            return;
        }
        FollowItemEventDO event = new FollowItemEventDO();
        event.setUserId(subscription.getUserId());
        event.setItemId(item.getId());
        event.setSource(source);
        event.setEventType(EVENT_TYPE_NOTICE_PUBLISHED);
        event.setNoticeId(notice.getId());
        event.setEventKey(eventKey);
        event.setTitleSnapshot(notice.getTitle());
        event.setOrigUrl(notice.getOrigUrl());
        event.setOccurredAt(LocalDateTime.now());
        event.setReadFlag(Boolean.FALSE);
        followItemEventMapper.insert(event);

        // 刷新关注行：最近动态/未读数/名称快照
        item.setLatestDynamicTime(event.getOccurredAt());
        item.setUnreadCount(item.getUnreadCount() == null ? 1 : item.getUnreadCount() + 1);
        if (StringUtils.hasText(notice.getProjectName())) {
            item.setDisplayName(notice.getProjectName());
        }
        if (StringUtils.hasText(notice.getIssuerName())) {
            item.setIssuerName(notice.getIssuerName());
        }
        followItemMapper.updateById(item);

        // 提醒留痕（IN_APP 必发；真实渠道 API 占位，见设计文档 §14.3）
        SubscriptionEventDO se = new SubscriptionEventDO();
        se.setUserId(subscription.getUserId());
        se.setFollowEventId(event.getId());
        se.setNoticeId(notice.getId());
        se.setChannel(CHANNEL_IN_APP);
        se.setChannelStatus("PENDING");
        se.setRetryCount(0);
        subscriptionEventMapper.insert(se);
    }

    private FollowItemDO newItem(Long userId, String followType, String followKey, NoticeExternalDO notice) {
        FollowItemDO item = new FollowItemDO();
        item.setUserId(userId);
        item.setFollowType(followType);
        item.setFollowKey(followKey);
        item.setDisplayName(StringUtils.hasText(notice.getProjectName()) ? notice.getProjectName() : notice.getTitle());
        item.setIssuerName(notice.getIssuerName());
        item.setStageCode("UNKNOWN");
        item.setUnreadCount(0);
        item.setFollowStatus(STATUS_FOLLOWING);
        item.setFollowTime(LocalDateTime.now());
        return item;
    }


    // ========== 私有工具 ==========

    private NoticeSubscriptionDO findSubscription(Long userId, String targetType, String targetKey) {
        return noticeSubscriptionMapper.selectOne(new LambdaQueryWrapper<NoticeSubscriptionDO>()
                .eq(NoticeSubscriptionDO::getUserId, userId)
                .eq(NoticeSubscriptionDO::getTargetType, targetType)
                .eq(NoticeSubscriptionDO::getTargetKey, targetKey)
                .last("LIMIT 1"));
    }

    private FollowItemDO findItem(Long userId, String followType, String followKey) {
        return followItemMapper.selectOne(new LambdaQueryWrapper<FollowItemDO>()
                .eq(FollowItemDO::getUserId, userId)
                .eq(FollowItemDO::getFollowType, followType)
                .eq(FollowItemDO::getFollowKey, followKey)
                .last("LIMIT 1"));
    }

    private void restoreFollowItem(NoticeSubscriptionDO subscription) {
        if (TYPE_ANNOUNCEMENT.equals(subscription.getTargetType())) {
            return;
        }
        FollowItemDO item = findItem(subscription.getUserId(), subscription.getTargetType(), subscription.getTargetKey());
        if (item != null) {
            restoreItem(item);
        }
    }

    private void restoreItem(FollowItemDO item) {
        item.setFollowStatus(STATUS_FOLLOWING);
        item.setFollowTime(LocalDateTime.now());
        item.setUnfollowTime(null);
        followItemMapper.updateById(item);
    }

    /** 订阅目标键与公告的 project/tender/section 任一匹配即命中 */
    private boolean matches(NoticeSubscriptionDO subscription, NoticeExternalDO notice) {
        if (subscription.getTargetKey() == null) {
            return false;
        }
        return subscription.getTargetKey().equals(notice.getProjectCode())
                || subscription.getTargetKey().equals(notice.getTenderProjectCode())
                || inSectionCodes(notice.getBidSectionCodes(), subscription.getTargetKey());
    }

    private boolean inSectionCodes(String bidSectionCodes, String targetKey) {
        if (!StringUtils.hasText(bidSectionCodes)) {
            return false;
        }
        for (String section : bidSectionCodes.split(",")) {
            if (section.trim().equals(targetKey)) {
                return true;
            }
        }
        return false;
    }

    /** 关注行存在才刷新；不存在则由订阅信息补建（displayName 取项目名/标题快照） */
    private void ensureItem(NoticeSubscriptionDO subscription, NoticeExternalDO notice) {
        if (TYPE_ANNOUNCEMENT.equals(subscription.getTargetType())) {
            return;
        }
        FollowItemDO item = findItem(subscription.getUserId(), subscription.getTargetType(), subscription.getTargetKey());
        if (item == null) {
            item = new FollowItemDO();
            item.setUserId(subscription.getUserId());
            item.setFollowType(subscription.getTargetType());
            item.setFollowKey(subscription.getTargetKey());
            item.setDisplayName(StringUtils.hasText(notice.getProjectName()) ? notice.getProjectName() : notice.getTitle());
            item.setIssuerName(notice.getIssuerName());
            item.setStageCode("UNKNOWN");
            item.setUnreadCount(0);
            item.setFollowStatus(STATUS_FOLLOWING);
            item.setFollowTime(LocalDateTime.now());
            followItemMapper.insert(item);
        }
    }

    private String eventKey(Long userId, Long itemId, String source, Long noticeId) {
        return userId + "-" + itemId + "-" + source + "-" + (noticeId == null ? "0" : noticeId);
    }

    private FollowItemRespVO toItemResp(FollowItemDO item) {
        FollowItemRespVO vo = new FollowItemRespVO();
        vo.setId(item.getId());
        vo.setFollowType(item.getFollowType());
        vo.setFollowKey(item.getFollowKey());
        vo.setDisplayName(item.getDisplayName());
        vo.setIssuerName(item.getIssuerName());
        vo.setStageCode(item.getStageCode());
        vo.setStageName(item.getStageName());
        vo.setBaseJson(item.getBaseJson());
        vo.setLatestDynamicTime(item.getLatestDynamicTime());
        vo.setUnreadCount(item.getUnreadCount());
        vo.setFollowTime(item.getFollowTime());
        return vo;
    }

    private FollowEventRespVO toEventResp(FollowItemEventDO event) {
        FollowEventRespVO vo = new FollowEventRespVO();
        vo.setId(event.getId());
        vo.setSource(event.getSource());
        vo.setEventType(event.getEventType());
        vo.setNoticeId(event.getNoticeId());
        vo.setTitleSnapshot(event.getTitleSnapshot());
        vo.setOrigUrl(event.getOrigUrl());
        vo.setOccurredAt(event.getOccurredAt());
        vo.setReadFlag(event.getReadFlag());
        return vo;
    }

}
