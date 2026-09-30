package cn.iocoder.txgy.module.bidding.controller.admin.bidreview;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssueRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewPortalCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewEnterpriseRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportDocRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskProgressRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewIssueDO;
import cn.iocoder.txgy.module.bidding.service.bidreview.BidReviewIssueService;
import cn.iocoder.txgy.module.bidding.service.bidreview.BidReviewReportService;
import cn.iocoder.txgy.module.bidding.service.bidreview.BidReviewReportDocService;
import cn.iocoder.txgy.module.bidding.service.bidreview.BidReviewTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 智慧标书审查 · 个人端（门户自助）接口。
 *
 * <p>方案依据：§2.6（独立命名空间 + .self 权限）、§4.1（最简路径：上传 → 一键生成 → 看报告）、§5.1。
 * 安全口径：入参一律不含 userId，归属人由 getLoginUserId() 决定，查询按 owner_user_id 过滤。
 *
 * <p>权限口径（2026-09-16 调整）：个人端<b>只要求登录</b>（{@code @PreAuthorize("isAuthenticated()")}），
 * 不再校验 {@code bidding:bid-review:self:*} 权限码——这些接口本身按登录人隔离（越权在 SQL 层已挡死），
 * 用权限码卡会变成"账号没配角色就完全用不了"（用户实测：部分账号无角色 → 403 没有操作权限）；
 * 管理端接口（task/issue/rule/config）仍保留权限码，高风险动作不会被放开。
 */
@Tag(name = "门户 - 智慧标书审查（个人端）")
@RestController
@RequestMapping("/bidding/bid-review/portal")
@Validated
public class BidReviewPortalController {

    @Resource
    private BidReviewTaskService taskService;
    @Resource
    private BidReviewIssueService issueService;
    @Resource
    private BidReviewReportService reportService;
    @Resource
    private BidReviewReportDocService reportDocService;

    @PostMapping("/create-and-generate")
    @Operation(summary = "上传后一键生成（创建任务并立即执行审查流水线）")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    public CommonResult<Long> createAndGenerate(@Valid @RequestBody BidReviewPortalCreateReqVO reqVO) {
        return success(taskService.createAndGenerateByPortal(reqVO));
    }

    @GetMapping("/my/page")
    @Operation(summary = "我的审查记录（仅本人）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<BidReviewTaskRespVO>> getMyPage(@Valid BidReviewTaskPageReqVO reqVO) {
        return success(taskService.getMyTaskPage(reqVO));
    }

    @GetMapping("/my-enterprises")
    @Operation(summary = "我已被授权为经办人的企业（上传页\"所属企业\"下拉用）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<BidReviewEnterpriseRespVO>> getMyEnterprises() {
        return success(taskService.getMyEnterprises());
    }

    @GetMapping("/simple-list")
    @Operation(summary = "我的最近审查记录（下拉用）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<BidReviewTaskRespVO>> getSimpleList() {
        return success(taskService.getMySimpleList());
    }

    @GetMapping("/get")
    @Operation(summary = "我的审查详情")
    @Parameter(name = "id", description = "任务 id", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<BidReviewTaskRespVO> get(@RequestParam("id") Long id) {
        return success(taskService.getMyTask(id));
    }

    @GetMapping("/progress")
    @Operation(summary = "进度轮询（轻量字段）")
    @Parameter(name = "id", description = "任务 id", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<BidReviewTaskProgressRespVO> progress(@RequestParam("id") Long id) {
        return success(taskService.getMyTaskProgress(id));
    }

    @GetMapping("/issue/list")
    @Operation(summary = "我的任务问题清单")
    @Parameter(name = "taskId", description = "任务 id", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<BidReviewIssueRespVO>> issueList(@RequestParam("taskId") Long taskId) {
        List<BidReviewIssueDO> issues = issueService.getMyIssueList(taskId);
        return success(issues.stream().map(issueService::toResp).toList());
    }

    @GetMapping("/report")
    @Operation(summary = "我的审查报告")
    @Parameter(name = "taskId", description = "任务 id", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<BidReviewReportRespVO> report(@RequestParam("taskId") Long taskId) {
        return success(reportService.getReportByTaskId(taskId, true));
    }

    @GetMapping("/report/doc")
    @Operation(summary = "生成/获取审查报告 Word 文档（可下载，同一任务只生成一次）")
    @Parameter(name = "taskId", description = "任务 id", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<BidReviewReportDocRespVO> reportDoc(@RequestParam("taskId") Long taskId) {
        return success(reportDocService.getOrCreateDocx(taskId, true));
    }

    @PostMapping("/retry")
    @Operation(summary = "失败重试（限 3 次）")
    @Parameter(name = "id", description = "任务 id", required = true)
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    public CommonResult<Boolean> retry(@RequestParam("id") Long id) {
        taskService.retryMyTask(id);
        return success(true);
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消任务")
    @Parameter(name = "id", description = "任务 id", required = true)
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        taskService.cancelMyTask(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除我的审查记录（逻辑删除）")
    @Parameter(name = "id", description = "任务 id", required = true)
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        taskService.deleteMyTask(id);
        return success(true);
    }
}
