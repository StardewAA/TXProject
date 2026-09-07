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

    /** 公告进入 ACTIVE（外部审核通过/内部发布）→ 匹配关注并写动态事件+提醒（同事件去重） */
    void handleNoticeActivated(NoticeExternalDO notice, String source);

}
