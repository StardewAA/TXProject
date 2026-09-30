package cn.iocoder.txgy.module.bidding.controller.admin.review;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskApproveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskRejectReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskStatsRespVO;
import cn.iocoder.txgy.module.bidding.service.review.ReviewTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 平台审查任务中心接口。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§4 权限与租户隔离 / §5 接口（5 个端点）。
 *
 * <p>为什么这样写（设计遵循）：
 * <ul>
 *   <li><b>可见范围由后端按身份决定</b>（§4）：拥有 `bidding:review:approve`/ `reject`（仅超管）即为平台视角，
 *       否则按租户视角强制过滤本企业 —— 前端隐藏按钮不算数，后端才是裁判。</li>
 *   <li><b>逐接口 @PreAuthorize</b>：query 共享给超管与租户，approve/reject 仅平台角色。</li>
 *   <li>`page`/`get`/`stats` 共用同一个"是否平台视角"的判定，口径只有一个入口，避免各接口各写一套。</li>
 * </ul>
 *
 * <p>业务目标：超管在共享界面统一筛选审查；租户只读查看本企业任务状态与驳回原因。
 *
 * @author txgy
 */
@Tag(name = "管理后台 - 平台审查任务中心")
@RestController
@RequestMapping("/bidding/review-task")
@Validated
public class ReviewTaskController {

    /** 平台审查角色标识（仅超管持有这两个权限码之一，见契约 §4）。 */
    private static final String PERM_APPROVE = "bidding:review:approve";
    private static final String PERM_REJECT = "bidding:review:reject";

    @Resource
    private ReviewTaskService reviewTaskService;

    @GetMapping("/page")
    @Operation(summary = "审查任务分页（超管跨企业筛选；租户自动只看本企业）")
    @PreAuthorize("@ss.hasPermission('bidding:review:query')")
    public CommonResult<PageResult<ReviewTaskRespVO>> page(@Valid ReviewTaskPageReqVO reqVO) {
        return success(reviewTaskService.getTaskPage(reqVO, !isPlatformReviewer()));
    }

    @GetMapping("/get")
    @Operation(summary = "审查任务详情")
    @Parameter(name = "id", description = "任务 id", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('bidding:review:query')")
    public CommonResult<ReviewTaskRespVO> get(@RequestParam("id") Long id) {
        return success(reviewTaskService.getTask(id, !isPlatformReviewer()));
    }

    @GetMapping("/stats")
    @Operation(summary = "审查任务统计（待审查/今日新增/已通过/已驳回；租户自动限定本企业）")
    @PreAuthorize("@ss.hasPermission('bidding:review:query')")
    public CommonResult<ReviewTaskStatsRespVO> stats() {
        return success(reviewTaskService.getStats(!isPlatformReviewer()));
    }

    @PostMapping("/approve")
    @Operation(summary = "审查通过（乐观锁：必须回传 version）")
    @Idempotent
    @PreAuthorize("@ss.hasPermission('bidding:review:approve')")
    public CommonResult<Boolean> approve(@Valid @RequestBody ReviewTaskApproveReqVO reqVO) {
        reviewTaskService.approve(reqVO.getTaskId(), reqVO.getVersion(), currentReviewerName());
        return success(true);
    }

    @PostMapping("/reject")
    @Operation(summary = "审查驳回（乐观锁 + 驳回原因必填留痕）")
    @Idempotent
    @PreAuthorize("@ss.hasPermission('bidding:review:reject')")
    public CommonResult<Boolean> reject(@Valid @RequestBody ReviewTaskRejectReqVO reqVO) {
        reviewTaskService.reject(reqVO.getTaskId(), reqVO.getVersion(), reqVO.getReason(), currentReviewerName());
        return success(true);
    }

    /**
     * 是否平台审查视角：持有通过或驳回权限之一即为平台（仅超管被授予，见契约 §4 授权指引）。
     *
     * <p>为什么用权限而不是角色名：全仓判权统一走权限码（@ss.hasPermission），角色名可被改名而权限码是契约。
     */
    private boolean isPlatformReviewer() {
        // 权限由 Spring Security 上下文承载（LoginUser 本身不带权限集合），故从 Authentication 的 authorities 判定。
        // 取不到上下文时按**最严**处理（租户只读），避免默认放宽导致越权读取他企业任务。
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (var authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (PERM_APPROVE.equals(value) || PERM_REJECT.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** 审查人（昵称优先，取不到回落到用户 id；两者都取不到返回 null —— 由库表允许为空承接）。 */
    private String currentReviewerName() {
        String nickname = SecurityFrameworkUtils.getLoginUserNickname();
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? null : String.valueOf(userId);
    }

}
