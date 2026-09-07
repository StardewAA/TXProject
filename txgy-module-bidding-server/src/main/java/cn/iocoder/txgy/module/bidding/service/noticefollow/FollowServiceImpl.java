package cn.iocoder.txgy.module.bidding.service.noticefollow;
// 规范依据：设计文档 v0.6 §12/§13/§15.1（T 轨拦截器隔离、事件幂等去重、IN_APP 留痕 API 占位）；
// 《招投标模块开发规范》§4.2/§4.5

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeStatusEnum;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowEventPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowEventRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowStatusRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.FollowItemDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.FollowItemEventDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.NoticeSubscriptionDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticefollow.SubscriptionEventDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注/关注项目列表 服务实现。
 */
@Service
public class FollowServiceImpl implements FollowService {

    /** 关注状态 */
    private static final String STATUS_FOLLOWING = "FOLLOWING";
    private static final String STATUS_UNFOLLOWED = "UNFOLLOWED";
    /** 关注维度类型（设计文档 §12.2）：公告/项目/招标项目/标段 */
    private static final String TYPE_ANNOUNCEMENT = "ANNOUNCEMENT";
    private static final String TYPE_PROJECT = "PROJECT";
    private static final String TYPE_TENDER_PROJECT = "TENDER_PROJECT";
    private static final String TYPE_SECTION = "SECTION";
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
    /** 公告主档 Mapper（G 轨）：公告级关注建行/恢复/读时快照标题与发布单位（N2①，跨轨只读不写） */
    @Resource
    private NoticeExternalMapper noticeExternalMapper;
    /** 内部公告 Mapper（T 轨同租户）：事件可达性反查（BR-4，INTERNAL 事件 noticeId → bidding_notice.status） */
    @Resource
    private NoticeMapper noticeMapper;

    @Override
    public void follow(Long userId, FollowReqVO reqVO) {
        // 目标类型白名单（设计文档 §12.2：ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION），拒绝拼写错误入库
        if (!TYPE_ANNOUNCEMENT.equals(reqVO.getTargetType())
                && !TYPE_PROJECT.equals(reqVO.getTargetType())
                && !TYPE_TENDER_PROJECT.equals(reqVO.getTargetType())
                && !TYPE_SECTION.equals(reqVO.getTargetType())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FOLLOW_TARGET_INVALID);
        }
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
            // 公告级关注：建行即快照标题/发布单位（N2①，避免“我的关注”显示一串雪花 id，等扇出才补名）
            snapshotAnnouncement(item, reqVO.getTargetType(), reqVO.getTargetKey());
            try {
                followItemMapper.insert(item);
            } catch (DuplicateKeyException e) {
                // 并发下唯一键兜底：忽略（另一请求已建行）
            }
            // BR-6 关注已生效公告的初始反馈：种已读初始动态（timeline 非空；幂等，仅首建行执行）
            seedInitialFeedback(item);
        } else if (!STATUS_FOLLOWING.equals(item.getFollowStatus())) {
            restoreItem(item);
            // 退订再关注：恢复行并刷新公告快照（标题更新后重关应看到新标题）
            snapshotAnnouncement(item, reqVO.getTargetType(), reqVO.getTargetKey());
            seedInitialFeedback(item);
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
        if (StringUtils.hasText(reqVO.getFollowKey())) {
            // 目标键过滤（N2①：公告级补名/详情页“是否已关注”可精确命中，不再全量扫描）
            wrapper.eq(FollowItemDO::getFollowKey, reqVO.getFollowKey());
        }
        IPage<FollowItemDO> page = followItemMapper.selectPage(Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(toItemRespList(page.getRecords()), page.getTotal());
    }

    /** 行映射（BR-4）：公告级行携带公告展示状态与平台可达性（available=false → 前端隐藏详情跳转、按源站原文兜底）。 */
    private List<FollowItemRespVO> toItemRespList(List<FollowItemDO> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<Long, NoticeExternalDO> noticeMap = fetchAnnouncementNotices(records);
        List<FollowItemRespVO> result = new ArrayList<>(records.size());
        for (FollowItemDO row : records) {
            Long keyId = row == null ? null : parseKeyId(row);
            NoticeExternalDO notice = keyId == null ? null : noticeMap.get(keyId); // 维度行 keyId=null：不入 map（MapN.get(null) 抛 NPE）
            if (notice != null) {
                applyNoticeSnapshot(row, notice);
            }
            result.add(toItemResp(row, notice));
        }
        return result;
    }

    /** 公告级行批量回查公告主档（读路径内存态：补名+状态；不落库）。 */
    private Map<Long, NoticeExternalDO> fetchAnnouncementNotices(List<FollowItemDO> rows) {
        if (noticeExternalMapper == null) {
            return Map.of();
        }
        List<Long> needIds = new ArrayList<>();
        for (FollowItemDO row : rows) {
            if (row == null || !TYPE_ANNOUNCEMENT.equals(row.getFollowType()) || !StringUtils.hasText(row.getFollowKey())) {
                continue;
            }
            Long id = parseKeyId(row);
            if (id != null) {
                needIds.add(id);
            }
        }
        if (needIds.isEmpty()) {
            return Map.of();
        }
        try {
            return noticeExternalMapper.selectBatchIds(needIds).stream()
                    .collect(Collectors.toMap(NoticeExternalDO::getId, n -> n, (a, b) -> a));
        } catch (Exception e) {
            return Map.of(); // 公告主档不可读不阻断列表（缺状态时 available=null，前端按无公告级状态处理）
        }
    }

    /** 公告级行目标 id（仅 ANNOUNCEMENT 且 key 为数字）；维度行为 null。 */
    private Long parseKeyId(FollowItemDO row) {
        if (row == null || !TYPE_ANNOUNCEMENT.equals(row.getFollowType()) || !StringUtils.hasText(row.getFollowKey())) {
            return null;
        }
        return parseLongKey(row.getFollowKey());
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
        return new PageResult<>(toEventRespList(page.getRecords()), page.getTotal());
    }

    /** 时间线事件映射（BR-4）：批量反查关联公告状态（EXTERNAL→G 轨外部主档；INTERNAL→本租户内部公告），避免逐条 N+1。 */
    private List<FollowEventRespVO> toEventRespList(List<FollowItemEventDO> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<Long> externalIds = new ArrayList<>();
        List<Long> internalIds = new ArrayList<>();
        for (FollowItemEventDO event : events) {
            if (event.getNoticeId() == null) {
                continue;
            }
            if (EVENT_SOURCE_EXTERNAL.equals(event.getSource())) {
                externalIds.add(event.getNoticeId());
            } else {
                internalIds.add(event.getNoticeId());
            }
        }
        Map<Long, String> externalStatus = fetchExternalNoticeStatuses(externalIds);
        Map<Long, String> internalStatus = fetchInternalNoticeStatuses(internalIds);
        List<FollowEventRespVO> result = new ArrayList<>(events.size());
        for (FollowItemEventDO event : events) {
            Map<Long, String> statusMap = EVENT_SOURCE_EXTERNAL.equals(event.getSource()) ? externalStatus : internalStatus;
            result.add(toEventResp(event, event.getNoticeId() == null ? null : statusMap.get(event.getNoticeId())));
        }
        return result;
    }

    private Map<Long, String> fetchExternalNoticeStatuses(List<Long> ids) {
        if (ids.isEmpty() || noticeExternalMapper == null) {
            return Map.of();
        }
        try {
            return noticeExternalMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(NoticeExternalDO::getId, NoticeExternalDO::getStatus, (a, b) -> a));
        } catch (Exception e) {
            return Map.of(); // 公告主档不可读：状态缺失（available=null），不阻断时间线
        }
    }

    private Map<Long, String> fetchInternalNoticeStatuses(List<Long> ids) {
        if (ids.isEmpty() || noticeMapper == null) {
            return Map.of();
        }
        try {
            return noticeMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(NoticeDO::getId, NoticeDO::getStatus, (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
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
    public FollowStatusRespVO getFollowStatus(Long userId, String targetType, String targetKey) {
        FollowStatusRespVO resp = new FollowStatusRespVO();
        resp.setFollowed(Boolean.FALSE);
        resp.setTargetType(targetType);
        resp.setTargetKey(targetKey);
        // 归属校验即 findSubscription（T 轨：租户由拦截器隔离，user 维度在此显式命中）
        NoticeSubscriptionDO sub = findSubscription(userId, targetType, targetKey);
        if (sub == null || !STATUS_FOLLOWING.equals(sub.getFollowStatus())) {
            return resp; // 未关注
        }
        resp.setFollowed(Boolean.TRUE);
        FollowItemDO item = findItem(userId, targetType, targetKey);
        if (item != null) {
            resp.setItemId(item.getId());
            String displayName = item.getDisplayName();
            String issuerName = item.getIssuerName();
            // 公告级且行快照缺名（雪花 id/空）：单点即时回查公告补名（不落库，避免读路径写放大）
            if (TYPE_ANNOUNCEMENT.equals(targetType)
                    && (!StringUtils.hasText(displayName) || Objects.equals(displayName, targetKey))) {
                NoticeExternalDO notice = findNoticeByFollowKey(targetKey);
                if (notice != null) {
                    if (StringUtils.hasText(notice.getProjectName())) {
                        displayName = notice.getProjectName();
                    } else if (StringUtils.hasText(notice.getTitle())) {
                        displayName = notice.getTitle();
                    }
                    if (StringUtils.hasText(notice.getIssuerName())) {
                        issuerName = notice.getIssuerName();
                    }
                }
            }
            resp.setDisplayName(displayName);
            resp.setIssuerName(issuerName);
        }
        return resp;
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

    /** 公告级关注行快照：写路径（建行/恢复）按公告主档填 displayName/issuerName（N2①）。 */
    private void snapshotAnnouncement(FollowItemDO item, String targetType, String targetKey) {
        if (!TYPE_ANNOUNCEMENT.equals(targetType) || item == null) {
            return;
        }
        NoticeExternalDO notice = findNoticeByFollowKey(targetKey);
        if (notice == null) {
            return; // 目标不可查：保留 fallback（displayName=targetKey，前端/读路径仍可兜底补名）
        }
        applyNoticeSnapshot(item, notice);
    }

    /**
     * 初始反馈（BR-6）：关注“已生效”外部公告（ACTIVE）时，种一条已读 EXTERNAL_ACTIVE 初始动态并刷新
     * 最近动态时间（“我的关注”timeline 立即有内容，避免用户以为没关注上）；未读角标不增加；维度关注不种。
     * 幂等：seedKey 固定（user-item-source-noticeId），重复关注/并发双写只落一条。
     * [依据] 《电子招标投标办法》第24条（项目跟踪业务）：关注即建立跟踪视图，初始态可直接呈现公告内容。
     */
    private void seedInitialFeedback(FollowItemDO item) {
        if (item == null || item.getId() == null || !TYPE_ANNOUNCEMENT.equals(item.getFollowType())) {
            return;
        }
        NoticeExternalDO notice = findNoticeByFollowKey(item.getFollowKey());
        if (notice == null || !NoticeExternalStatusEnum.ACTIVE.getStatus().equals(notice.getStatus())) {
            return; // 未生效/不可查：等审核生效扇出（handleNoticeActivated）再提醒
        }
        String seedKey = item.getUserId() + "-" + item.getId() + "-" + EVENT_SOURCE_EXTERNAL
                + "-" + notice.getId() + "-seed";
        FollowItemEventDO exists = followItemEventMapper.selectOne(new LambdaQueryWrapper<FollowItemEventDO>()
                .eq(FollowItemEventDO::getEventKey, seedKey));
        if (exists != null) {
            return;
        }
        FollowItemEventDO event = new FollowItemEventDO();
        event.setUserId(item.getUserId());
        event.setItemId(item.getId());
        event.setSource(EVENT_SOURCE_EXTERNAL);
        event.setEventType(EVENT_TYPE_EXTERNAL_ACTIVE);
        event.setNoticeId(notice.getId());
        event.setEventKey(seedKey);
        event.setTitleSnapshot(notice.getTitle());
        event.setOrigUrl(notice.getOrigUrl());
        event.setOccurredAt(LocalDateTime.now());
        event.setReadFlag(Boolean.TRUE); // 初始动态已读：不制造未读提醒
        try {
            followItemEventMapper.insert(event);
        } catch (DuplicateKeyException e) {
            return; // 并发双写兜底（event_key 唯一约束）
        }
        item.setLatestDynamicTime(event.getOccurredAt());
        followItemMapper.updateById(item);
    }

    /** 读路径补名（页面列表/单点状态）：公告主档 → 行快照字段（仅内存态，不落库）。 */
    private void applyNoticeSnapshot(FollowItemDO item, NoticeExternalDO notice) {
        if (StringUtils.hasText(notice.getProjectName())) {
            item.setDisplayName(notice.getProjectName());
        } else if (StringUtils.hasText(notice.getTitle())) {
            item.setDisplayName(notice.getTitle());
        }
        if (StringUtils.hasText(notice.getIssuerName())) {
            item.setIssuerName(notice.getIssuerName());
        }
    }

    /** targetKey → 公告主档（仅 ANNOUNCEMENT 且 key 为数字 id 时查；G 轨只读）。 */
    private NoticeExternalDO findNoticeByFollowKey(String targetKey) {
        if (noticeExternalMapper == null || !StringUtils.hasText(targetKey)) {
            return null;
        }
        Long noticeId = parseLongKey(targetKey);
        if (noticeId == null) {
            return null;
        }
        try {
            return noticeExternalMapper.selectById(noticeId);
        } catch (Exception e) {
            return null; // 公告主档不可读（如 G 轨表未部署）不阻断关注读路径
        }
    }

    private Long parseLongKey(String targetKey) {
        try {
            return Long.valueOf(targetKey.trim());
        } catch (NumberFormatException e) {
            return null; // 维度代码（非数字）非公告 id
        }
    }

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
            // 退订再关注（走既有订阅恢复路径）：公告级同样刷新快照
            snapshotAnnouncement(item, subscription.getTargetType(), subscription.getTargetKey());
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

    private FollowItemRespVO toItemResp(FollowItemDO item, NoticeExternalDO notice) {
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
        // BR-4 可达性：公告级行携带公告展示状态与平台可达性（公告 ACTIVE 才可达平台详情）
        String noticeStatus = notice == null ? null : notice.getStatus();
        vo.setNoticeStatus(noticeStatus);
        vo.setAvailable(availableOf(EVENT_SOURCE_EXTERNAL, noticeStatus));
        return vo;
    }

    /** 可达性（BR-4）：公告“展示中”（EXTERNAL=ACTIVE / INTERNAL=PUBLISHED）才可跳平台详情；无公告状态返回 null。 */
    private Boolean availableOf(String source, String noticeStatus) {
        if (noticeStatus == null) {
            return null;
        }
        if (EVENT_SOURCE_EXTERNAL.equals(source)) {
            return NoticeExternalStatusEnum.ACTIVE.getStatus().equals(noticeStatus);
        }
        return NoticeStatusEnum.PUBLISHED.name().equals(noticeStatus);
    }

    /** 事件行映射（BR-4）：按来源反查公告状态，事件携带 noticeStatus/available（下线公告前端隐藏详情跳转）。 */
    private FollowEventRespVO toEventResp(FollowItemEventDO event, String noticeStatus) {
        FollowEventRespVO vo = new FollowEventRespVO();
        vo.setId(event.getId());
        vo.setSource(event.getSource());
        vo.setEventType(event.getEventType());
        vo.setNoticeId(event.getNoticeId());
        vo.setTitleSnapshot(event.getTitleSnapshot());
        vo.setOrigUrl(event.getOrigUrl());
        vo.setOccurredAt(event.getOccurredAt());
        vo.setReadFlag(event.getReadFlag());
        vo.setNoticeStatus(noticeStatus);
        vo.setAvailable(availableOf(event.getSource(), noticeStatus));
        return vo;
    }

}
