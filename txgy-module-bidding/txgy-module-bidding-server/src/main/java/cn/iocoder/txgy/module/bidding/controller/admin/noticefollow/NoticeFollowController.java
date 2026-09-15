package cn.iocoder.txgy.module.bidding.controller.admin.noticefollow;
// 规范依据：设计文档 v0.6 §12.4/§13.5（登录投标人关注/关注项目列表/时间线/已读）；§14.3（通知渠道 API 占位）

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowEventPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowEventRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo.FollowStatusRespVO;
import cn.iocoder.txgy.module.bidding.service.noticefollow.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 管理后台 - 关注/关注项目列表（投标人登录态；关注主体 = system AdminUser，设计文档 §15.5）。
 */
@Tag(name = "管理后台 - bidding 关注/关注项目列表")
@RestController
@RequestMapping("/bidding/notice-follow")
@Validated
public class NoticeFollowController {

    @Resource
    private FollowService followService;

    @PostMapping("/follow")
    @Idempotent
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Operation(summary = "关注（公告/项目/招标项目/标段，幂等）")
    public CommonResult<Boolean> follow(@Valid @RequestBody FollowReqVO reqVO) {
        followService.follow(getLoginUserId(), reqVO);
        return success(Boolean.TRUE);
    }

    @PostMapping("/unfollow")
    @Idempotent
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Operation(summary = "取消关注（幂等）")
    public CommonResult<Boolean> unfollow(@Valid @RequestBody FollowReqVO reqVO) {
        followService.unfollow(getLoginUserId(), reqVO);
        return success(Boolean.TRUE);
    }

    @GetMapping("/item/page")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:query')")
    @Operation(summary = "我的关注项目列表（阶段/基础信息/最新动态/未读数；followType/followKey 过滤）")
    public CommonResult<PageResult<FollowItemRespVO>> pageMyFollow(@Valid FollowItemPageReqVO reqVO) {
        return success(followService.pageMyFollow(getLoginUserId(), reqVO));
    }

    @GetMapping("/status")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:query')")
    @Operation(summary = "关注状态单点查询（详情页关注按钮初始态；返回 {followed,itemId,快照}）")
    public CommonResult<FollowStatusRespVO> status(@RequestParam("targetType") String targetType,
                                                   @RequestParam("targetKey") String targetKey) {
        return success(followService.getFollowStatus(getLoginUserId(), targetType, targetKey));
    }

    @GetMapping("/item/{itemId}/timeline")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:query')")
    @Operation(summary = "关注项时间线（该项目公告列表+动态）")
    public CommonResult<PageResult<FollowEventRespVO>> timeline(@PathVariable("itemId") Long itemId,
                                                                @Valid FollowEventPageReqVO reqVO) {
        return success(followService.pageTimeline(getLoginUserId(), itemId, reqVO));
    }

    @PostMapping("/item/{itemId}/read")
    @Idempotent
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Operation(summary = "标记关注项已读（清未读）")
    public CommonResult<Boolean> markRead(@PathVariable("itemId") Long itemId) {
        followService.markRead(getLoginUserId(), itemId);
        return success(Boolean.TRUE);
    }

}
