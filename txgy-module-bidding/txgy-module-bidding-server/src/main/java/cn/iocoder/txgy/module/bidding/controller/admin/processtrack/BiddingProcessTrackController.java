package cn.iocoder.txgy.module.bidding.controller.admin.processtrack;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackRegisterReqVO;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackRespVO;
import cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo.ProcessTrackUploadReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackItemVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackOptionsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackReviewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo.ProcessTrackStatsRespVO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.dal.dataobject.processtrack.BiddingProcessTrackDO;
import cn.iocoder.txgy.module.bidding.service.processtrack.BiddingProcessTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - bidding 投标流程跟踪（投标方视角：意向投标→报名→资质审核→递交加密投标文件）")
@RestController
@RequestMapping("/bidding/process-track")
@Validated
public class BiddingProcessTrackController {

    @Resource
    private BiddingProcessTrackService processTrackService;

    @PostMapping("/create")
    // [契约《门户投标个人用户提示与资格门》3.2] 改 isAuthenticated()，把"能否投标"的判定交给
    // Service 层门禁（assertBidPortalAllowed）—— 权限码 403 无法区分"个人用户未开放"与"档案未完善"，
    // 且前端对租户越权 403 会清会话（表现为"已登录却要求登录"）。门禁给出的是业务错误码。
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "意向投标建档（由用户显式发起，非关注即建档；每用户每标段一条，幂等）",
            description = "门禁顺序：个人用户 → 106_049；本租户未建档 → 106_109_037；未授权/未完善 → 106_031/106_034")
    @Idempotent
    public CommonResult<Long> create(@Valid @RequestBody ProcessTrackCreateReqVO reqVO) {
        return success(processTrackService.createTrack(reqVO).getId());
    }

    @PostMapping("/register")
    // 同 /create：业务码门禁替代权限码（见上）
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "报名投标（INTENTION→QUALIFICATION_REVIEW）")
    @Idempotent
    public CommonResult<ProcessTrackRespVO> register(@Valid @RequestBody ProcessTrackRegisterReqVO reqVO) {
        return success(BeanUtils.toBean(processTrackService.register(reqVO), ProcessTrackRespVO.class));
    }

    @PostMapping("/review")
    @PreAuthorize("@ss.hasPermission('bidding:process-track:review')")
    @Operation(summary = "资质审核（平台方；QUALIFICATION_REVIEW→PENDING_BID 或 TERMINATED）")
    @Idempotent
    public CommonResult<ProcessTrackRespVO> review(@Valid @RequestBody ProcessTrackReviewReqVO reqVO) {
        return success(BeanUtils.toBean(processTrackService.review(reqVO), ProcessTrackRespVO.class));
    }

    @PostMapping("/upload")
    // 同 /create：业务码门禁替代权限码（见上）
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "递交加密投标文件（PENDING_BID→BIDDED；截止后禁止；CA 加密+SM3 指纹+SM2 签名由外部 CA SDK 完成）")
    @Idempotent
    public CommonResult<ProcessTrackRespVO> upload(@Valid @RequestBody ProcessTrackUploadReqVO reqVO) {
        return success(BeanUtils.toBean(processTrackService.uploadDocument(reqVO), ProcessTrackRespVO.class));
    }

    @PostMapping("/cancel")
    @PreAuthorize("@ss.hasPermission('bidding:process-track:cancel')")
    @Operation(summary = "取消投标（非终态→TERMINATED）")
    @Idempotent
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id, @RequestParam("userId") Long userId) {
        processTrackService.cancel(id, userId);
        return success(true);
    }

    @GetMapping("/my")
    // 同 /create：个人用户与无角色账号应得到业务码而非 403（前端据此提示"未对个人开放"）
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "查询我的投标流程列表")
    public CommonResult<List<ProcessTrackRespVO>> my(@RequestParam("userId") Long userId) {
        // 契约《门户投标个人用户提示与资格门》3.2/附录：个人用户调"我的投标"应得到业务码
        // 「本功能未对个人用户开放」，而不是 @PreAuthorize 的 403（前端据此分流提示，且不触发会话清理）。
        processTrackService.assertBidPortalAllowed();
        return success(processTrackService.listMyWithAttachments(userId));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:process-track:query')")
    @Operation(summary = "查询单个投标流程")
    public CommonResult<ProcessTrackRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(processTrackService.getTrack(id), ProcessTrackRespVO.class));
    }

    // ==================== 管理端总览（09-18 契约 §5） ====================

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:process-track:query')")
    @Operation(summary = "总览分页（项目粒度，出参自带公司/负责人信息；超管跨租户，租户只看本公司）")
    public CommonResult<PageResult<ProcessTrackItemVO>> overviewPage(@Valid ProcessTrackPageReqVO reqVO) {
        return success(processTrackService.getOverviewPage(reqVO, isSuperAdmin()));
    }

    @GetMapping("/stats")
    @PreAuthorize("@ss.hasPermission('bidding:process-track:query')")
    @Operation(summary = "KPI 统计（公司数/项目数/进行中/已投标/已结束/负责人数/按阶段分布）")
    public CommonResult<ProcessTrackStatsRespVO> overviewStats() {
        return success(processTrackService.getOverviewStats(isSuperAdmin()));
    }

    @GetMapping("/options")
    @PreAuthorize("@ss.hasPermission('bidding:process-track:query')")
    @Operation(summary = "筛选项（负责人候选全量去重；公司候选仅超管下发）")
    public CommonResult<ProcessTrackOptionsRespVO> overviewOptions() {
        return success(processTrackService.getOverviewOptions(isSuperAdmin()));
    }

    @GetMapping("/detail")
    @PreAuthorize("@ss.hasPermission('bidding:process-track:query')")
    @Operation(summary = "详情（总览项 + 操作记录事件史 events[]，只读）")
    public CommonResult<ProcessTrackDetailRespVO> overviewDetail(@RequestParam("id") Long id) {
        return success(processTrackService.getOverviewDetail(id, isSuperAdmin()));
    }

    /**
     * 超管判定（09-18 契约 §6.2）：scopes 含 bidding:process-track:overview 即超管。
     * 该权限码仅绑 super_admin（role_id=1，见 2026-09-28-process-track-overview.sql），
     * 本部署权限码下发为去重菜单码，无 *:*:* 通配符，不能用 hasRole('*:*:*') 判定。
     */
    private boolean isSuperAdmin() {
        var user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getScopes() == null) {
            return false;
        }
        return user.getScopes().contains("bidding:process-track:overview");
    }
}
