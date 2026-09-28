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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    /**
     * 标段维度键的分隔符（契约 §四 健壮性修正）。
     *
     * <p>库内规范口径是半角分号，但爬虫侧对中文分号「；」/顿号「、」的解析结果也会落到该列。
     * 原实现 split("[,;]") 只认半角，会让含中文分隔符的标段**静默失配** ——
     * 既不出现在匹配键里、也不报错，属静默数据丢失。读取侧放宽，落库口径不变。
     */
    private static final String SECTION_KEY_SEPARATORS = "[，,；;、\\s]+";

    /**
     * 维度关注回填历史公告的条数上限（契约 §三 方案 A 的"上限保护"）。
     *
     * <p>早期维度（一个项目下可能有上千条公告）若全量回填，会让时间线与未读数一次性失控。
     * 取 50：足够让用户看到"这个维度有内容、最近有哪些"，又不至于淹没。
     * 超出部分不丢失 —— 后续新公告仍走正常扇出；这里只决定"初始视图给多少"。
     */
    private static final int DIMENSION_SEED_LIMIT = 50;

    /** 事件/提醒相关常量 */
    private static final String EVENT_TYPE_NOTICE_PUBLISHED = "NOTICE_PUBLISHED";
    private static final String EVENT_TYPE_NOTICE_UPDATED = "NOTICE_UPDATED";
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

    /** 行映射（BR-4/业务核查）：公告级行双源（外部 G 轨/内部 T 轨）回查——补名+展示状态+可达性。 */
    private List<FollowItemRespVO> toItemRespList(List<FollowItemDO> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<Long, NoticeExternalDO> externalMap = fetchExternalAnnouncementNotices(records);
        Map<Long, NoticeDO> internalMap = fetchInternalAnnouncementNotices(records, externalMap);
        List<FollowItemRespVO> result = new ArrayList<>(records.size());
        for (FollowItemDO row : records) {
            Long keyId = row == null ? null : parseKeyId(row);
            if (keyId == null) {
                result.add(toItemResp(row, null, null));
                continue;
            }
            NoticeExternalDO external = externalMap.get(keyId); // MapN.get(null) 已规避
            NoticeDO internal = external == null ? internalMap.get(keyId) : null;
            if (external != null) {
                applyNoticeSnapshot(row, external);
            } else if (internal != null) {
                row.setDisplayName(internal.getTitle());
                if (StringUtils.hasText(internal.getPublisherName())) {
                    row.setIssuerName(internal.getPublisherName());
                }
            }
            // 2026-09-09 修复：PROJECT 关注行补名——按 project_code 回查最新公告填项目名/发布单位（此前显示一串编号）
            if (row != null && TYPE_PROJECT.equals(row.getFollowType())
                    && (row.getDisplayName() == null || row.getDisplayName().equals(row.getFollowKey()))) {
                enrichProjectFromNotice(row);
            }
            result.add(toItemResp(row, external, internal));
        }
        return result;
    }

    /** 公告级行批量回查外部公告主档（G 轨，读路径内存态：补名+状态；不落库）。 */
    private Map<Long, NoticeExternalDO> fetchExternalAnnouncementNotices(List<FollowItemDO> rows) {
        if (noticeExternalMapper == null) {
            return Map.of();
        }
        List<Long> needIds = announcementIds(rows);
        if (needIds.isEmpty()) {
            return Map.of();
        }
        try {
            return noticeExternalMapper.selectBatchIds(needIds).stream()
                    .collect(Collectors.toMap(NoticeExternalDO::getId, n -> n, (a, b) -> a));
        } catch (Exception e) {
            return Map.of(); // 外部公告主档不可读不阻断列表
        }
    }

    /** 公告级行中外部未命中的 id，回查内部公告（T 轨同租户；内部公告级关注同样补名/状态）。 */
    private Map<Long, NoticeDO> fetchInternalAnnouncementNotices(List<FollowItemDO> rows,
                                                                 Map<Long, NoticeExternalDO> externalMap) {
        if (noticeMapper == null) {
            return Map.of();
        }
        List<Long> needIds = new ArrayList<>();
        for (Long id : announcementIds(rows)) {
            if (!externalMap.containsKey(id)) {
                needIds.add(id);
            }
        }
        if (needIds.isEmpty()) {
            return Map.of();
        }
        try {
            return noticeMapper.selectBatchIds(needIds).stream()
                    .collect(Collectors.toMap(NoticeDO::getId, n -> n, (a, b) -> a));
        } catch (Exception e) {
            return Map.of(); // 内部公告不可读不阻断列表
        }
    }

    private List<Long> announcementIds(List<FollowItemDO> rows) {
        List<Long> ids = new ArrayList<>();
        for (FollowItemDO row : rows) {
            if (row == null || !TYPE_ANNOUNCEMENT.equals(row.getFollowType()) || !StringUtils.hasText(row.getFollowKey())) {
                continue;
            }
            Long id = parseKeyId(row);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
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
            // 公告级且行快照缺名（雪花 id/空）：单点即时回查公告补名（不落库，避免读路径写放大）；
            // 双源：先外部（G 轨），未命中再内部（T 轨同租户）
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
                } else {
                    NoticeDO internal = findInternalNoticeByFollowKey(targetKey);
                    if (internal != null) {
                        displayName = internal.getTitle();
                        if (StringUtils.hasText(internal.getPublisherName())) {
                            issuerName = internal.getPublisherName();
                        }
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

    @Override
    public cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeRefanoutRespVO
            refanout(Long noticeId, boolean dryRun) {
        cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeRefanoutRespVO resp =
                new cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeRefanoutRespVO();
        resp.setDryRun(dryRun);
        resp.setMatched(0);
        resp.setCreated(0);
        resp.setSkipped(0);
        if (noticeId == null) {
            return resp;
        }
        NoticeExternalDO notice = noticeExternalMapper.selectById(noticeId);
        // 只对已生效公告重放：DRAFT/PENDING 等状态本就不该有提醒，重放它们会制造假动态
        if (notice == null || !NoticeExternalStatusEnum.ACTIVE.getStatus().equals(notice.getStatus())) {
            return resp;
        }
        // 与 handleNoticeActivated 同一套匹配口径（维度键 + 公告级），避免"重放命中数与线上不一致"
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
            Set<Long> seen = new HashSet<>();
            result.removeIf(s -> !seen.add(s.getId()));
            return result;
        });
        int matched = 0;
        int created = 0;
        for (NoticeSubscriptionDO subscription : subscriptions) {
            if (!matches(subscription, notice)) {
                continue;
            }
            matched++;
            if (dryRun) {
                continue;   // 预演：只数命中，不写库
            }
            Boolean ok = TenantUtils.execute(subscription.getTenantId(),
                    () -> handleForSubscriber(subscription, notice, EVENT_SOURCE_EXTERNAL));
            if (Boolean.TRUE.equals(ok)) {
                created++;
            }
        }
        resp.setMatched(matched);
        resp.setCreated(created);
        // 跳过数 = 命中 - 新建（重放时 created=0、skipped=matched，即"已提醒过，无需重复"）
        resp.setSkipped(matched - created);
        log.info("[follow][refanout] noticeId={} dryRun={} matched={} created={} skipped={}",
                noticeId, dryRun, matched, created, resp.getSkipped());
        return resp;
    }

    /**
     * 内部公告版本变化扇出（业务核查补全 2026-09-07）：发布首版/已发布内容更新时通知同租户关注者。
     * 匹配：公告级订阅（ANNOUNCEMENT, targetKey=公告id）+ 三级编号订阅（PROJECT=notice.projectCode /
     * TENDER_PROJECT=payload.TENDER_PROJECT_CODE / SECTION∈payload.BID_SECTION_CODES）；
     * 事件键含动作与版本号：同一版本幂等（重放安全），新版本/重新发布产生新动态。
     */
    @Override
    public void handleInternalNoticeChanged(String action, Long noticeId, String title, String publisherName,
                                            String projectName, String projectCode, String tenderProjectCode,
                                            List<String> bidSectionCodes, int version) {
        if (noticeId == null || action == null) {
            return;
        }
        String eventType = "PUBLISHED".equals(action)
                ? EVENT_TYPE_NOTICE_PUBLISHED : EVENT_TYPE_NOTICE_UPDATED;
        List<NoticeSubscriptionDO> subscriptions = new ArrayList<>();
        try {
            subscriptions.addAll(noticeSubscriptionMapper.selectList(new LambdaQueryWrapper<NoticeSubscriptionDO>()
                    .eq(NoticeSubscriptionDO::getFollowStatus, STATUS_FOLLOWING)
                    .eq(NoticeSubscriptionDO::getTargetType, TYPE_ANNOUNCEMENT)
                    .eq(NoticeSubscriptionDO::getTargetKey, String.valueOf(noticeId))));
            if (StringUtils.hasText(projectCode)) {
                subscriptions.addAll(noticeSubscriptionMapper.selectList(new LambdaQueryWrapper<NoticeSubscriptionDO>()
                        .eq(NoticeSubscriptionDO::getFollowStatus, STATUS_FOLLOWING)
                        .eq(NoticeSubscriptionDO::getTargetType, TYPE_PROJECT)
                        .eq(NoticeSubscriptionDO::getTargetKey, projectCode)));
            }
            // 三级编号（2026-09-23 补）：门户「我的关注」按三种编号建关注键，上面只查了 PROJECT，
            // 招标项目级/标段级关注者结构上永远命中不到 → 本站公告发出去没人收到动态。
            if (StringUtils.hasText(tenderProjectCode)) {
                subscriptions.addAll(noticeSubscriptionMapper.selectList(new LambdaQueryWrapper<NoticeSubscriptionDO>()
                        .eq(NoticeSubscriptionDO::getFollowStatus, STATUS_FOLLOWING)
                        .eq(NoticeSubscriptionDO::getTargetType, TYPE_TENDER_PROJECT)
                        .eq(NoticeSubscriptionDO::getTargetKey, tenderProjectCode)));
            }
            if (bidSectionCodes != null && !bidSectionCodes.isEmpty()) {
                subscriptions.addAll(noticeSubscriptionMapper.selectList(new LambdaQueryWrapper<NoticeSubscriptionDO>()
                        .eq(NoticeSubscriptionDO::getFollowStatus, STATUS_FOLLOWING)
                        .eq(NoticeSubscriptionDO::getTargetType, TYPE_SECTION)
                        .in(NoticeSubscriptionDO::getTargetKey, bidSectionCodes)));
            }
        } catch (Exception e) {
            log.warn("[follow][internal] 订阅查询失败 noticeId={}", noticeId, e);
            return;
        }
        Set<Long> seen = new HashSet<>();
        subscriptions.removeIf(s -> !seen.add(s.getId()));
        for (NoticeSubscriptionDO subscription : subscriptions) {
            try {
                fanoutInternal(subscription, eventType, noticeId, title, publisherName, projectName, version);
            } catch (Exception e) {
                log.warn("[follow][internal][sub={}] 动态写入失败 noticeId={}", subscription.getId(), noticeId, e);
            }
        }
    }

    /** 单订阅内部事件：建/恢复关注行 → 事件去重(版本) → 刷行 → IN_APP 留痕。 */
    private void fanoutInternal(NoticeSubscriptionDO subscription, String eventType, Long noticeId, String title,
                                String publisherName, String projectName, int version) {
        FollowItemDO item = findItem(subscription.getUserId(), subscription.getTargetType(), subscription.getTargetKey());
        if (item == null) {
            item = newInternalItem(subscription.getUserId(), subscription.getTargetType(), subscription.getTargetKey(),
                    title, publisherName, projectName);
            try {
                followItemMapper.insert(item);
            } catch (DuplicateKeyException e) {
                item = findItem(subscription.getUserId(), subscription.getTargetType(), subscription.getTargetKey());
            }
        }
        if (item == null || item.getId() == null) {
            return;
        }
        String key = subscription.getUserId() + "-" + item.getId() + "-" + EVENT_SOURCE_INTERNAL
                + "-" + noticeId + "-" + eventType + "-" + version;
        FollowItemEventDO exists = followItemEventMapper.selectOne(new LambdaQueryWrapper<FollowItemEventDO>()
                .eq(FollowItemEventDO::getEventKey, key));
        if (exists != null) {
            return; // 同一版本已提醒
        }
        FollowItemEventDO event = new FollowItemEventDO();
        event.setUserId(subscription.getUserId());
        event.setItemId(item.getId());
        event.setSource(EVENT_SOURCE_INTERNAL);
        event.setEventType(eventType);
        event.setNoticeId(noticeId);
        event.setEventKey(key);
        event.setTitleSnapshot(title);
        event.setOccurredAt(LocalDateTime.now());
        event.setReadFlag(Boolean.FALSE);
        followItemEventMapper.insert(event);
        item.setLatestDynamicTime(event.getOccurredAt());
        item.setUnreadCount(item.getUnreadCount() == null ? 1 : item.getUnreadCount() + 1);
        if (TYPE_ANNOUNCEMENT.equals(item.getFollowType())) {
            item.setDisplayName(title);
        } else if (StringUtils.hasText(projectName)) {
            item.setDisplayName(projectName);
        }
        if (StringUtils.hasText(publisherName)) {
            item.setIssuerName(publisherName);
        }
        followItemMapper.updateById(item);
        SubscriptionEventDO se = new SubscriptionEventDO();
        se.setUserId(subscription.getUserId());
        se.setFollowEventId(event.getId());
        se.setNoticeId(noticeId);
        se.setChannel(CHANNEL_IN_APP);
        se.setChannelStatus("PENDING");
        se.setRetryCount(0);
        subscriptionEventMapper.insert(se);
    }

    /** 内部公告关注行（公告级快照=公告标题；项目级=项目名兜底标题）。 */
    private FollowItemDO newInternalItem(Long userId, String followType, String followKey, String title,
                                         String publisherName, String projectName) {
        FollowItemDO item = new FollowItemDO();
        item.setUserId(userId);
        item.setFollowType(followType);
        item.setFollowKey(followKey);
        item.setFollowStatus(STATUS_FOLLOWING);
        item.setFollowTime(LocalDateTime.now());
        item.setDisplayName(TYPE_ANNOUNCEMENT.equals(followType)
                ? title : (StringUtils.hasText(projectName) ? projectName : title));
        item.setIssuerName(publisherName);
        return item;
    }

    /**
     * 在订阅者租户上下文中执行：建关注行/事件去重/刷行/提醒留痕。
     *
     * @return true=本次新建了动态事件（产生了新提醒）；false=事件键已存在被去重跳过（重放幂等的正常表现）
     */
    private boolean handleForSubscriber(NoticeSubscriptionDO subscription, NoticeExternalDO notice, String source) {
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
            return false;   // 去重跳过：重放同一公告不会重复提醒（幂等语义）
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
        return true;
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
        if (notice != null) {
            applyNoticeSnapshot(item, notice);
            return;
        }
        // 内部公告（同租户）：关注建行/恢复时按内部主档填标题/发布单位
        NoticeDO internal = findInternalNoticeByFollowKey(targetKey);
        if (internal == null) {
            return; // 目标不可查：保留 fallback（displayName=targetKey，读路径仍可兜底补名）
        }
        item.setDisplayName(internal.getTitle());
        if (StringUtils.hasText(internal.getPublisherName())) {
            item.setIssuerName(internal.getPublisherName());
        }
    }

    /** PROJECT 关注行补名：按 followKey(=project_code) 回查公告库最新匹配公告，填 displayName/issuerName（落库快照，避免列表显示一串编号）。 */
    private void enrichProjectFromNotice(FollowItemDO item) {
        if (item == null || noticeExternalMapper == null || !StringUtils.hasText(item.getFollowKey())) {
            return;
        }
        try {
            NoticeExternalDO notice = noticeExternalMapper.selectOne(new LambdaQueryWrapper<NoticeExternalDO>()
                    .eq(NoticeExternalDO::getProjectCode, item.getFollowKey())
                    .orderByDesc(NoticeExternalDO::getPublishTime)
                    .last("LIMIT 1"));
            if (notice == null) {
                // project_code 未命中：tender_project_code 兜底（关注键可能是招标项目编号）
                notice = noticeExternalMapper.selectOne(new LambdaQueryWrapper<NoticeExternalDO>()
                        .eq(NoticeExternalDO::getTenderProjectCode, item.getFollowKey())
                        .orderByDesc(NoticeExternalDO::getPublishTime)
                        .last("LIMIT 1"));
            }
            if (notice == null) {
                return; // 库中无对应项目公告：保留 fallback（编号），有新公告扇出时再补
            }
            if (StringUtils.hasText(notice.getProjectName())) {
                item.setDisplayName(notice.getProjectName());
            } else if (StringUtils.hasText(notice.getTitle())) {
                item.setDisplayName(notice.getTitle());
            }
            if (StringUtils.hasText(notice.getIssuerName())) {
                item.setIssuerName(notice.getIssuerName());
            }
            item.setStageCode("UNKNOWN"); // 阶段推进能力见设计排期（F8），本期保持占位
            followItemMapper.updateById(item);
        } catch (Exception e) {
            // 回查失败不阻断列表（保持 fallback 编号）
        }
    }

    /** targetKey → 内部公告主档（T 轨同租户；仅 ANNOUNCEMENT 数字 id）。 */
    private NoticeDO findInternalNoticeByFollowKey(String targetKey) {
        if (noticeMapper == null || !StringUtils.hasText(targetKey)) {
            return null;
        }
        Long noticeId = parseLongKey(targetKey);
        if (noticeId == null) {
            return null;
        }
        try {
            return noticeMapper.selectById(noticeId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 初始反馈（BR-6）：关注“已生效”外部公告（ACTIVE）时，种一条已读 EXTERNAL_ACTIVE 初始动态并刷新
     * 最近动态时间（“我的关注”timeline 立即有内容，避免用户以为没关注上）；未读角标不增加；维度关注不种。
     * 幂等：seedKey 固定（user-item-source-noticeId），重复关注/并发双写只落一条。
     * [依据] 《电子招标投标办法》第24条（项目跟踪业务）：关注即建立跟踪视图，初始态可直接呈现公告内容。
     */
    private void seedInitialFeedback(FollowItemDO item) {
        if (item == null || item.getId() == null) {
            return;
        }
        // 维度关注（PROJECT/TENDER_PROJECT/SECTION）：回填该维度下已生效的历史公告（契约 §三 方案 A）
        if (!TYPE_ANNOUNCEMENT.equals(item.getFollowType())) {
            seedDimensionInitial(item);
            return;
        }
        NoticeExternalDO notice = findNoticeByFollowKey(item.getFollowKey());
        if (notice != null && NoticeExternalStatusEnum.ACTIVE.getStatus().equals(notice.getStatus())) {
            seedExternalInitial(item, notice);
            return;
        }
        // 内部公告（T 轨同租户）：已发布（PUBLISHED）同样种已读初始动态（业务对称：关注即见首版动态）
        NoticeDO internal = findInternalNoticeByFollowKey(item.getFollowKey());
        if (internal == null || !NoticeStatusEnum.PUBLISHED.name().equals(internal.getStatus())) {
            return; // 未生效/不可查：等发布/审核扇出再提醒
        }
        seedInternalInitial(item, internal);
    }

    /**
     * 维度关注的初始回填（契约《关注扇出重放补偿与维度回填-2026-09-29》§三 方案 A）。
     *
     * <p><b>为什么需要</b>：此前 {@code seedInitialFeedback} 对维度类型直接 return ——
     * 用户关注一个**项目/标段**后，该维度下**已发布的历史公告一条都不会进时间线**
     * （实测两个维度关注项的 latest_dynamic_time 均为 NULL），用户体感是"关注功能是坏的"。
     *
     * <p><b>口径</b>（与公告级初始反馈对齐）：
     * <ul>
     *   <li>{@code readFlag=true} —— 回填不制造未读轰炸（初始视图是"给你看看已有什么"，不是"提醒"）；</li>
     *   <li>幂等键沿用 seed 语义（user-item-source-noticeId-seed），重复关注/并发双写自动去重；</li>
     *   <li>**上限保护**：按发布时间倒序最多 {@link #DIMENSION_SEED_LIMIT} 条 ——
     *       早期维度可能关联上千条公告，一次灌入会让时间线与未读数失控；</li>
     *   <li>复用 {@code matches()} 的维度匹配，不新造口径（避免"回填能看到、扇出看不到"的漂移）。</li>
     * </ul>
     */
    private void seedDimensionInitial(FollowItemDO item) {
        // 2026-09-23 契约 B2：**本站公告回填先行且独立**。
        // 必须放在外部公告的 early-return 之前——否则"该维度没有外部公告"时
        // 本站公告的回填会被一并跳过（实测踩到：TENDER_PROJECT 维度外部无命中时，
        // 本站公告明明有数据却回填不出来）。两个数据源互不依赖，各自幂等。
        seedInternalDimensionInitial(item);
        // 以下为外部公告（G 轨）维度回填：数据源与维度键落点均与本站不同，故分开处理
        List<NoticeExternalDO> notices;
        try {
            notices = TenantUtils.executeIgnore(() -> noticeExternalMapper.selectList(
                    new LambdaQueryWrapper<NoticeExternalDO>()
                            .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.ACTIVE.getStatus())
                            // 维度键列：project_code / tender_project_code / bid_section_codes
                            .and(w -> w.eq(NoticeExternalDO::getProjectCode, item.getFollowKey())
                                    .or().eq(NoticeExternalDO::getTenderProjectCode, item.getFollowKey())
                                    .or().apply("FIND_IN_SET({0}, REPLACE(bid_section_codes, ';', ',')) > 0",
                                            item.getFollowKey()))
                            .orderByDesc(NoticeExternalDO::getPublishTime)
                            .last("LIMIT " + DIMENSION_SEED_LIMIT)));
        } catch (Exception e) {
            log.warn("[follow][seed-dimension] 维度历史公告查询失败 itemId={} key={}", item.getId(), item.getFollowKey(), e);
            return;
        }
        if (notices == null || notices.isEmpty()) {
            return; // 该维度暂无已发布公告：等扇出（前端已按维度提示覆盖率原因）
        }
        LocalDateTime latest = null;
        int seeded = 0;
        for (NoticeExternalDO notice : notices) {
            // 复用扇出匹配口径：确保"回填能看到的"与"将来扇出会提醒的"是同一批
            if (!matchesFollowKey(item.getFollowType(), item.getFollowKey(), notice)) {
                continue;
            }
            if (seedExternalInitialQuiet(item, notice)) {
                seeded++;
            }
            if (latest == null || (notice.getPublishTime() != null && notice.getPublishTime().isAfter(latest))) {
                latest = notice.getPublishTime();
            }
        }
        // 最近动态时间取该维度下最新一篇公告的发布时间（不用 now()——那是"回填时刻"，不是内容时间）
        if (seeded > 0 && latest != null) {
            item.setLatestDynamicTime(latest);
            followItemMapper.updateById(item);
        }
        log.info("[follow][seed-dimension] itemId={} type={} key={} 命中={} 回填={}",
                item.getId(), item.getFollowType(), item.getFollowKey(), notices.size(), seeded);
    }

    /**
     * 本站公告（T 轨）维度回填：与 {@link #seedDimensionInitial} **同构**，只是数据源换成
     * {@code bidding_notice}、匹配键换成「根字段 project_code + payload 两级编号」。
     *
     * <p><b>为什么与外部公告分开两个方法而不是合并</b>：
     * 两者的维度键落点不同（外部公告是三列 project_code/tender_project_code/bid_section_codes；
     * 本站公告是根字段 + {@code fields_json} 的 TENDER_PROJECT_CODE / BID_SECTION_CODES），
     * 查询语句无法共用；但**匹配口径必须一致**（见 {@link #matchesInternalFollowKey}），
     * 否则会出现"回填能看到、扇出看不到"的漂移——这正是契约 §4 提示的风险。
     *
     * <p><b>三条关键语义（照外部公告先例，勿改）</b>：
     * <ol>
     *   <li>{@code readFlag=true} —— 回填**不制造未读轰炸**（初始视图是"给你看看已有什么"，不是"提醒"）；
     *       未读数留给后续真实扇出；</li>
     *   <li>幂等键 {@code user-item-INTERNAL-noticeId-seed}（与扇出事件键区分，重复关注/并发自动去重）；</li>
     *   <li>上限 {@link #DIMENSION_SEED_LIMIT} 条、按发布时间倒序 —— 早期项目可能关联大量公告，
     *       一次灌入会让时间线失控；超出部分不丢，后续新公告仍走正常扇出。</li>
     * </ol>
     *
     * <p><b>只回填 PUBLISHED</b>：草稿/已撤下/已归档不对关注者展示（与公开可见性口径一致），
     * 故不纳入初始视图。
     */
    private void seedInternalDimensionInitial(FollowItemDO item) {
        if (noticeMapper == null || !StringUtils.hasText(item.getFollowKey())) {
            return;
        }
        List<NoticeDO> notices;
        try {
            // 跨租户读：与外部公告回填一致（关注行的 tenant 与公告 tenant 可能不同视角），
            // 只读、且仅取维度键匹配的 PUBLISHED 行
            notices = TenantUtils.executeIgnore(() -> noticeMapper.selectList(
                    new LambdaQueryWrapper<NoticeDO>()
                            .eq(NoticeDO::getStatus, NoticeStatusEnum.PUBLISHED.name())
                            .and(w -> w.eq(NoticeDO::getProjectCode, item.getFollowKey())
                                    .or().apply("JSON_UNQUOTE(JSON_EXTRACT(fields_json, '$.TENDER_PROJECT_CODE')) = {0}",
                                            item.getFollowKey())
                                    .or().apply("FIND_IN_SET({0}, REPLACE(REPLACE(REPLACE(REPLACE("
                                                    + "JSON_UNQUOTE(JSON_EXTRACT(fields_json, '$.BID_SECTION_CODES')),"
                                                    + " ';', ','), '；', ','), '、', ','), ' ', ',')) > 0",
                                            item.getFollowKey()))
                            .orderByDesc(NoticeDO::getPublishTime)
                            .last("LIMIT " + DIMENSION_SEED_LIMIT)));
        } catch (Exception e) {
            // 本站公告回填失败不影响外部公告回填结果（两者独立）；记录便于排查
            log.warn("[follow][seed-dimension-internal] 本站公告回填查询失败 itemId={} key={}",
                    item.getId(), item.getFollowKey(), e);
            return;
        }
        if (notices == null || notices.isEmpty()) {
            return; // 该维度暂无已发布本站公告：等发布时扇出
        }
        int seeded = 0;
        LocalDateTime latest = null;
        for (NoticeDO notice : notices) {
            // 复用**扇出同一套**匹配口径：确保"回填能看到的"与"将来扇出会提醒的"是同一批
            if (!matchesInternalFollowKey(item.getFollowType(), item.getFollowKey(), notice)) {
                continue;
            }
            if (seedInternalDimensionQuiet(item, notice)) {
                seeded++;
            }
            if (latest == null || (notice.getPublishTime() != null && notice.getPublishTime().isAfter(latest))) {
                latest = notice.getPublishTime();
            }
        }
        // 最近动态时间取该维度下最新一篇的发布时间（不用 now()——那是"回填时刻"，不是内容时间）
        if (seeded > 0 && latest != null) {
            item.setLatestDynamicTime(latest);
            followItemMapper.updateById(item);
        }
        log.info("[follow][seed-dimension-internal] itemId={} type={} key={} 命中={} 回填={}",
                item.getId(), item.getFollowType(), item.getFollowKey(), notices.size(), seeded);
    }

    /**
     * 本站公告的维度键匹配（与 {@link #matchesFollowKey} 对外部公告的口径**逐条对应**）。
     *
     * <p>对应关系：
     * <ul>
     *   <li>{@code PROJECT} → 根字段 {@code notice.projectCode}</li>
     *   <li>{@code TENDER_PROJECT} → {@code fields_json.TENDER_PROJECT_CODE}</li>
     *   <li>{@code SECTION} → {@code fields_json.BID_SECTION_CODES} 的任一分量</li>
     *   <li>{@code ANNOUNCEMENT} → 公告 id（本站公告同样支持按公告关注）</li>
     * </ul>
     *
     * <p>标段分隔符口径与扇出侧 {@code matchesSection} 及查询侧保持一致
     * （半角/全角分号、顿号、逗号、空白）。
     */
    private boolean matchesInternalFollowKey(String followType, String followKey, NoticeDO notice) {
        if (!StringUtils.hasText(followKey) || notice == null) {
            return false;
        }
        if (TYPE_ANNOUNCEMENT.equals(followType)) {
            return followKey.equals(String.valueOf(notice.getId()));
        }
        if (TYPE_PROJECT.equals(followType)) {
            return followKey.equals(notice.getProjectCode());
        }
        if (TYPE_TENDER_PROJECT.equals(followType)) {
            return followKey.equals(payloadText(notice, "TENDER_PROJECT_CODE"));
        }
        if (TYPE_SECTION.equals(followType)) {
            String codes = payloadText(notice, "BID_SECTION_CODES");
            if (!StringUtils.hasText(codes)) {
                return false;
            }
            for (String part : codes.split("[,，、;；\\s]+")) {
                if (followKey.equals(part.trim())) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /**
     * 读 {@code fields_json} 指定字段为字符串（缺失/非法返回 null，不抛错）。
     *
     * <p><b>为什么用轻量正则而不是引入 ObjectMapper</b>：
     * 本类当前没有 JSON 解析依赖，为读两个已知字段名（TENDER_PROJECT_CODE / BID_SECTION_CODES）
     * 引入整套 Jackson 绑定不划算，且解析失败路径需要额外兜底。
     * 两字段都是**扁平字符串**（非嵌套、非数组），正则提取足够且零异常面；
     * 若将来要读嵌套结构，再换成 ObjectMapper。
     *
     * <p>注意：返回的是**原始字符串**（含可能的空白），比较时由调用方 trim。
     */
    private String payloadText(NoticeDO notice, String field) {
        if (notice == null || !StringUtils.hasText(notice.getFieldsJson())) {
            return null;
        }
        // "FIELD":"value" —— 容忍 key/value 两侧空格（json 序列化风格差异）
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(notice.getFieldsJson());
        return m.find() ? m.group(1) : null;
    }

    /**
     * 本站公告维度初始动态（**已读**，幂等 key：{@code user-item-INTERNAL-noticeId-seed}）。
     *
     * <p>与公告级 {@link #seedInternalInitial} 的区别：事件类型同为 NOTICE_PUBLISHED、
     * 幂等键构成相同，故二者**天然互斥**（同一公告同一条关注行不会产生两条 seed 事件）；
     * 本方法额外带上公告快照（项目名/发布单位），使维度视图与公告级视图展示一致。
     *
     * @return true=本次确实写入了新事件（用于统计回填条数）
     */
    private boolean seedInternalDimensionQuiet(FollowItemDO item, NoticeDO notice) {
        String seedKey = item.getUserId() + "-" + item.getId() + "-" + EVENT_SOURCE_INTERNAL
                + "-" + notice.getId() + "-seed";
        FollowItemEventDO exists = followItemEventMapper.selectOne(new LambdaQueryWrapper<FollowItemEventDO>()
                .eq(FollowItemEventDO::getEventKey, seedKey));
        if (exists != null) {
            return false;
        }
        FollowItemEventDO event = new FollowItemEventDO();
        event.setUserId(item.getUserId());
        event.setItemId(item.getId());
        event.setSource(EVENT_SOURCE_INTERNAL);
        event.setEventType(EVENT_TYPE_NOTICE_PUBLISHED);
        event.setNoticeId(notice.getId());
        event.setEventKey(seedKey);
        event.setTitleSnapshot(notice.getTitle());
        // occurredAt 用公告发布时间：维度视图按"内容时间"排序，回填时刻没有业务含义
        event.setOccurredAt(notice.getPublishTime() != null ? notice.getPublishTime() : LocalDateTime.now());
        event.setReadFlag(Boolean.TRUE); // 初始动态已读：不制造未读提醒
        try {
            followItemEventMapper.insert(event);
        } catch (DuplicateKeyException e) {
            return false; // 并发双写兜底（event_key 唯一约束）
        }
        return true;
    }

    /** 外部公告初始动态（BR-6，原实现内联拆出）。 */
    private void seedExternalInitial(FollowItemDO item, NoticeExternalDO notice) {
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

    /** 内部公告初始动态（已读 NOTICE_PUBLISHED，快照=关注时标题；幂等 key：user-item-INTERNAL-noticeId-seed）。 */
    private void seedInternalInitial(FollowItemDO item, NoticeDO internal) {
        String seedKey = item.getUserId() + "-" + item.getId() + "-" + EVENT_SOURCE_INTERNAL
                + "-" + internal.getId() + "-seed";
        FollowItemEventDO exists = followItemEventMapper.selectOne(new LambdaQueryWrapper<FollowItemEventDO>()
                .eq(FollowItemEventDO::getEventKey, seedKey));
        if (exists != null) {
            return;
        }
        FollowItemEventDO event = new FollowItemEventDO();
        event.setUserId(item.getUserId());
        event.setItemId(item.getId());
        event.setSource(EVENT_SOURCE_INTERNAL);
        event.setEventType(EVENT_TYPE_NOTICE_PUBLISHED);
        event.setNoticeId(internal.getId());
        event.setEventKey(seedKey);
        event.setTitleSnapshot(internal.getTitle());
        event.setOccurredAt(LocalDateTime.now());
        event.setReadFlag(Boolean.TRUE);
        try {
            followItemEventMapper.insert(event);
        } catch (DuplicateKeyException e) {
            return;
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

    /**
     * 公告维度键集合（project/tender + 标段拆分）。
     *
     * <p>[契约 §四 健壮性修正] 分隔符由 {@code [,;]} 扩为 {@code [，,；;、\s]+} ——
     * 爬虫侧对中文分号「；」/顿号「、」的解析结果会写成这些字符，原实现只认半角分号/逗号，
     * 会让这些标段**静默失配**（既不出现在匹配键里，也不报错），属静默数据丢失。
     * 规范落库口径仍是半角分号，此处只是读取侧容错。
     */
    private Set<String> collectMatchKeys(NoticeExternalDO notice) {
        Set<String> keys = new HashSet<>();
        if (StringUtils.hasText(notice.getProjectCode())) {
            keys.add(notice.getProjectCode());
        }
        if (StringUtils.hasText(notice.getTenderProjectCode())) {
            keys.add(notice.getTenderProjectCode());
        }
        if (StringUtils.hasText(notice.getBidSectionCodes())) {
            for (String section : notice.getBidSectionCodes().split(SECTION_KEY_SEPARATORS)) {
                String trimmed = section.trim();
                if (StringUtils.hasText(trimmed)) {
                    keys.add(trimmed);
                }
            }
        }
        return keys;
    }

    /**
     * 维度匹配（与 {@link #matches(NoticeSubscriptionDO, NoticeExternalDO)} 的维度分支同口径）。
     *
     * <p>抽出来是为了让"回填"与"扇出"共用同一判定 —— 否则会出现
     * "回填进时间线的公告，扇出时却不认"（或反之）的口径漂移。
     */
    private boolean matchesFollowKey(String followType, String followKey, NoticeExternalDO notice) {
        if (!StringUtils.hasText(followKey) || notice == null) {
            return false;
        }
        if (TYPE_ANNOUNCEMENT.equals(followType)) {
            return followKey.equals(String.valueOf(notice.getId()));
        }
        // PROJECT / TENDER_PROJECT / SECTION 三类共用维度键判定（与 matches() 一致）
        return followKey.equals(notice.getProjectCode())
                || followKey.equals(notice.getTenderProjectCode())
                || inSectionCodes(notice.getBidSectionCodes(), followKey);
    }

    /**
     * 种一条已读的维度回填动态（{@link #seedExternalInitial} 的静默版）。
     *
     * <p>与公告级 seed 的差异：**不刷新 item.latestDynamicTime**（由调用方按该维度最新公告的
     * 发布时间统一设置，而不是每条都覆盖成 now()）；返回是否真的新建，
     * 供调用方统计回填条数。
     *
     * @return true=新建了一条回填事件；false=已存在（幂等跳过）或写冲突
     */
    private boolean seedExternalInitialQuiet(FollowItemDO item, NoticeExternalDO notice) {
        // 幂等键沿用 seed 语义，与公告级回填共用一套去重（重复关注/并发双写自动跳过）
        String seedKey = item.getUserId() + "-" + item.getId() + "-" + EVENT_SOURCE_EXTERNAL
                + "-" + notice.getId() + "-seed";
        FollowItemEventDO exists = followItemEventMapper.selectOne(new LambdaQueryWrapper<FollowItemEventDO>()
                .eq(FollowItemEventDO::getEventKey, seedKey));
        if (exists != null) {
            return false;
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
        // 发生时间用公告的发布时间（内容时间），而非回填时刻 —— 否则时间线会把历史公告显示成"刚刚"
        event.setOccurredAt(notice.getPublishTime() == null ? LocalDateTime.now() : notice.getPublishTime());
        event.setReadFlag(Boolean.TRUE); // 回填=已读：初始视图不制造未读轰炸
        try {
            followItemEventMapper.insert(event);
            return true;
        } catch (DuplicateKeyException e) {
            return false; // 并发双写兜底（event_key 唯一约束）
        }
    }

    private boolean inSectionCodes(String bidSectionCodes, String targetKey) {
        if (!StringUtils.hasText(bidSectionCodes)) {
            return false;
        }
        for (String section : bidSectionCodes.split(SECTION_KEY_SEPARATORS)) {
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

    private FollowItemRespVO toItemResp(FollowItemDO item, NoticeExternalDO external, NoticeDO internal) {
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
        // BR-4/业务核查 可达性：公告级行双源（外部 ACTIVE / 内部 PUBLISHED）才可达平台详情
        if (external != null) {
            vo.setNoticeStatus(external.getStatus());
            vo.setAvailable(availableOf(EVENT_SOURCE_EXTERNAL, external.getStatus()));
        } else if (internal != null) {
            vo.setNoticeStatus(internal.getStatus());
            vo.setAvailable(availableOf(EVENT_SOURCE_INTERNAL, internal.getStatus()));
        }
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
