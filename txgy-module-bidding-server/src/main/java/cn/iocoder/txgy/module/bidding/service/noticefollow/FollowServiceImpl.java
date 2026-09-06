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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 关注/关注项目列表 服务实现。
 */
@Service
public class FollowServiceImpl implements FollowService {

    /** 关注状态 */
    private static final String STATUS_FOLLOWING = "FOLLOWING";
    private static final String STATUS_UNFOLLOWED = "UNFOLLOWED";
    /** 公告级关注也建关注项目行（展示“我关注的公告”，标题快照在激活扇出时刷新） */
    private static final String TYPE_ANNOUNCEMENT = "ANNOUNCEMENT";
    /** 事件/提醒相关常量 */
    private static final String EVENT_TYPE_NOTICE_PUBLISHED = "NOTICE_PUBLISHED";
    /** 外部公告审核生效提醒（设计 §12.2/前端 02 文档事件枚举 EXTERNAL_ACTIVE） */
    private static final String EVENT_TYPE_EXTERNAL_ACTIVE = "EXTERNAL_ACTIVE";
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
        try {
            noticeSubscriptionMapper.insert(subscription);
        } catch (DuplicateKeyException e) {
            return; // 并发双插：唯一键 uk_sub_user_target 兜底，另一请求已建订阅（幂等）
        }
        // 关注项目列表行：全类型建立（含公告级关注，展示“我关注的公告”；标题快照在扇出时刷新）
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
            try {
                followItemMapper.insert(item);
            } catch (DuplicateKeyException e) {
                // 并发下唯一键兜底：忽略（另一请求已建行）
            }
        } else if (!STATUS_FOLLOWING.equals(item.getFollowStatus())) {
            restoreItem(item);
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
        FollowItemDO item = findItem(userId, reqVO.getTargetType(), reqVO.getTargetKey());
        if (item != null) {
            item.setFollowStatus(STATUS_UNFOLLOWED);
            item.setUnfollowTime(LocalDateTime.now());
            followItemMapper.updateById(item);
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
        // 平台侧触发（无登录租户）：跨租户读取全部关注（拦截器忽略）
        // 按公告维度键反查命中订阅（project/tender/section IN + 公告级 targetKey=noticeId），不再全表扫描后内存过滤
        List<NoticeSubscriptionDO> subscriptions = TenantUtils.executeIgnore(() -> {
            List<NoticeSubscriptionDO> result = new ArrayList<>();
            Set<String> keys = collectMatchKeys(notice);
            if (!keys.isEmpty()) {
                result.addAll(noticeSubscriptionMapper.selectList(new LambdaQueryWrapper<NoticeSubscriptionDO>()
                        .eq(NoticeSubscriptionDO::getFollowStatus, STATUS_FOLLOWING)
                        .in(NoticeSubscriptionDO::getTargetKey, keys)));
            }
            result.addAll(noticeSubscriptionMapper.selectList(new LambdaQueryWrapper<NoticeSubscriptionDO>()
                    .eq(NoticeSubscriptionDO::getFollowStatus, STATUS_FOLLOWING)
                    .eq(NoticeSubscriptionDO::getTargetType, TYPE_ANNOUNCEMENT)
                    .eq(NoticeSubscriptionDO::getTargetKey, String.valueOf(notice.getId()))));
            // 去重（同一订阅不可能同时命中维度与公告两查，但保底）
            Set<Long> seen = new HashSet<>();
            result.removeIf(s -> !seen.add(s.getId()));
            return result;
        });
        for (NoticeSubscriptionDO subscription : subscriptions) {
            if (matches(subscription, notice)) {
                TenantUtils.execute(subscription.getTenantId(), () -> handleForSubscriber(subscription, notice, source));
            }
        }
    }

    /** 在订阅者租户上下文中执行：建关注行/事件去重/刷行/提醒留痕 */
    private void handleForSubscriber(NoticeSubscriptionDO subscription, NoticeExternalDO notice, String source) {
        // 事件去重：同一 (user,item,source,notice,contentHash) 只写一条（event_key 幂等；内容版本变化会形成新 key → 新版本提醒）
        FollowItemDO item = findItem(subscription.getUserId(), subscription.getTargetType(), subscription.getTargetKey());
        if (item == null) {
            item = newItem(subscription.getUserId(), subscription.getTargetType(), subscription.getTargetKey(), notice);
            try {
                followItemMapper.insert(item);
            } catch (DuplicateKeyException e) {
                item = findItem(subscription.getUserId(), subscription.getTargetType(), subscription.getTargetKey());
            }
        }
        String eventKey = eventKey(subscription.getUserId(), item.getId(), source, notice.getId(), notice.getContentHash());
        FollowItemEventDO exists = followItemEventMapper.selectOne(new LambdaQueryWrapper<FollowItemEventDO>()
                .eq(FollowItemEventDO::getEventKey, eventKey));
        if (exists != null) {
            return;
        }
        FollowItemEventDO event = new FollowItemEventDO();
        event.setUserId(subscription.getUserId());
        event.setItemId(item.getId());
        event.setSource(source);
        event.setEventType(EVENT_SOURCE_EXTERNAL.equals(source) ? EVENT_TYPE_EXTERNAL_ACTIVE : EVENT_TYPE_NOTICE_PUBLISHED);
        event.setNoticeId(notice.getId());
        event.setEventKey(eventKey);
        event.setTitleSnapshot(notice.getTitle());
        event.setOrigUrl(notice.getOrigUrl());
        event.setOccurredAt(LocalDateTime.now());
        event.setReadFlag(Boolean.FALSE);
        followItemEventMapper.insert(event);

        // 刷新关注行：最近动态/未读数/名称快照（公告级关注 displayName 落到公告标题）
        item.setLatestDynamicTime(event.getOccurredAt());
        item.setUnreadCount(item.getUnreadCount() == null ? 1 : item.getUnreadCount() + 1);
        if (StringUtils.hasText(notice.getProjectName())) {
            item.setDisplayName(notice.getProjectName());
        } else if (StringUtils.hasText(notice.getTitle())) {
            item.setDisplayName(notice.getTitle());
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

    /**
     * 订阅目标键与公告匹配（区分关注维度）：
     * <ul>
     *   <li>ANNOUNCEMENT（公告级关注）：targetKey = 公告主键 id 字符串 → 精确命中该公告（设计 §12.2 关注公告本身）；</li>
     *   <li>PROJECT/TENDER_PROJECT/SECTION（维度关注）：targetKey 与公告 project/tender/section 任一匹配即命中。</li>
     * </ul>
     */
    private boolean matches(NoticeSubscriptionDO subscription, NoticeExternalDO notice) {
        if (subscription.getTargetKey() == null) {
            return false;
        }
        if (TYPE_ANNOUNCEMENT.equals(subscription.getTargetType())) {
            return subscription.getTargetKey().equals(String.valueOf(notice.getId()));
        }
        return subscription.getTargetKey().equals(notice.getProjectCode())
                || subscription.getTargetKey().equals(notice.getTenderProjectCode())
                || inSectionCodes(notice.getBidSectionCodes(), subscription.getTargetKey());
    }

    /** 公告维度键集合（project/tender + 标段拆分；分隔符兼容半角分号/逗号，规范落库口径=半角分号） */
    private Set<String> collectMatchKeys(NoticeExternalDO notice) {
        Set<String> keys = new HashSet<>();
        if (StringUtils.hasText(notice.getProjectCode())) {
            keys.add(notice.getProjectCode());
        }
        if (StringUtils.hasText(notice.getTenderProjectCode())) {
            keys.add(notice.getTenderProjectCode());
        }
        if (StringUtils.hasText(notice.getBidSectionCodes())) {
            for (String section : notice.getBidSectionCodes().split("[,;]")) {
                String trimmed = section.trim();
                if (StringUtils.hasText(trimmed)) {
                    keys.add(trimmed);
                }
            }
        }
        return keys;
    }

    private boolean inSectionCodes(String bidSectionCodes, String targetKey) {
        if (!StringUtils.hasText(bidSectionCodes)) {
            return false;
        }
        for (String section : bidSectionCodes.split("[,;]")) {
            if (section.trim().equals(targetKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 事件幂等键：用户×关注项×来源×公告 × 内容指纹。
     * 含 contentHash：同一公告源站内容更新重新审核通过后再次提醒（设计 §12.2 新版本提醒）；内容未变的重放（幂等 PUT/重复消费）不重复提醒。
     */
    private String eventKey(Long userId, Long itemId, String source, Long noticeId, String contentHash) {
        return userId + "-" + itemId + "-" + source + "-" + (noticeId == null ? "0" : noticeId)
                + "-" + (StringUtils.hasText(contentHash) ? contentHash.substring(0, Math.min(12, contentHash.length())) : "nohash");
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
