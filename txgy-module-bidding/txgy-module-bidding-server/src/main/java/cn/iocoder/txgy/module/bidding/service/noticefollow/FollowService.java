package cn.iocoder.txgy.module.bidding.service.noticefollow;
// 规范依据：设计文档 v0.6 §12/§13/§15.1（T 轨，租户过滤交拦截器；登录投标人=system AdminUser）

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowEventPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowEventRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowStatusRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;

import java.util.List;

/**
 * 关注/关注项目列表 服务（投标人订阅 公告/项目/招标项目/标段）。
 *
 * <p>核心不变量：
 * <ul>
 *   <li>关注幂等：同一 (用户, targetType, targetKey) 重复关注返回原状态，不重复建行；退订再关注恢复 FOLLOWING；</li>
 *   <li>动态去重：同一用户同一事件（event_key）只写一条，未读才提醒；</li>
 *   <li>外部公告审核转 ACTIVE（或内部公告发布）后调用 {@link #handleNoticeActivated} 匹配关注并发提醒。</li>
 * </ul>
 */
public interface FollowService {

    /** 关注（幂等）：公告/项目/招标项目/标段；全类型（含公告级 ANNOUNCEMENT）同步建/恢复关注项目行 */
    void follow(Long userId, FollowReqVO reqVO);

    /** 取消关注（幂等）：订阅置 UNFOLLOWED，关注项目行同步置 UNFOLLOWED（保留历史） */
    void unfollow(Long userId, FollowReqVO reqVO);

    /** 我的关注项目列表（“关注的项目列表”界面，stage/基础信息/未读数/最近动态） */
    PageResult<FollowItemRespVO> pageMyFollow(Long userId, FollowItemPageReqVO reqVO);

    /** 单个关注项的时间线（该项目公告列表+动态，分页倒序） */
    PageResult<FollowEventRespVO> pageTimeline(Long userId, Long itemId, FollowEventPageReqVO reqVO);

    /** 标记关注项已读：清未读数并将该行事件全部置已读 */
    void markRead(Long userId, Long itemId);

    /** 关注状态单点查询（详情页关注按钮初始态；公告级缺快照时即时补名返回，不落库） */
    FollowStatusRespVO getFollowStatus(Long userId, String targetType, String targetKey);

    /** 公告进入 ACTIVE（外部审核通过）→ 匹配关注并写动态事件+提醒（同事件去重） */
    void handleNoticeActivated(NoticeExternalDO notice, String source);

    /**
     * 关注扇出重放（契约《关注扇出重放补偿与维度回填-2026-09-29》§一）。
     *
     * <p><b>为什么需要</b>：状态变更此前只有"应用审核接口"一条触发路径 ——
     * 批量导入、DBA 改库、数据迁移、刷数脚本都会绕过扇出（实测证据：2026-09-14 有 1055 条公告
     * 同一秒被批量 UPDATE 为 ACTIVE，creator/updater 均为 NULL，扇出从未触发，该批订阅一条提醒都没有）。
     * 且 {@code approveExternal} 里扇出失败**只记日志**，一次失败 = 该提醒永久丢失。
     *
     * <p><b>为什么重放安全</b>：事件键含 contentHash（用户-关注项-来源-公告id-contentHash），
     * 重复调用不产生重复提醒，仅"内容确实变过"才新建事件 —— 幂等是它能作为补偿手段的前提。
     *
     * @param noticeId 公告主键（须为 ACTIVE，否则直接返回全 0）
     * @param dryRun   true=只统计命中数不写库（排查"为什么没提醒"时先跑这个）
     * @return 命中/新建/跳过 统计
     */
    cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeRefanoutRespVO
            refanout(Long noticeId, boolean dryRun);

    /**
     * 内部公告版本变化（发布首版 NOTICE_PUBLISHED / 已发布内容更新产生新版本 NOTICE_UPDATED）：
     * 在发布者租户内匹配关注（公告级 targetKey=noticeId；项目级 targetKey=projectCode），
     * 写 INTERNAL 事件（事件键含版本号：同一版本幂等，新版本再提醒）与 IN_APP 提醒留痕。
     */
    void handleInternalNoticeChanged(String action, Long noticeId, String title, String publisherName,
                                     String projectName, String projectCode, String tenderProjectCode,
                                     List<String> bidSectionCodes, int version);

}
