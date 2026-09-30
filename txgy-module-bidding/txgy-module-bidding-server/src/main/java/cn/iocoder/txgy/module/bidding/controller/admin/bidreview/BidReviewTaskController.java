package cn.iocoder.txgy.module.bidding.controller.admin.bidreview;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewOverviewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskRespVO;
import cn.iocoder.txgy.module.bidding.service.bidreview.BidReviewTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 智慧标书审查 · 管理端任务接口（看板/建单/重跑/取消/统计）。
 *
 * <p>方案依据：§5.2。
 */
@Tag(name = "管理后台 - 智慧标书审查任务")
@RestController
@RequestMapping("/bidding/bid-review/task")
@Validated
public class BidReviewTaskController {

    @Resource
    private BidReviewTaskService taskService;

    @GetMapping("/page")
    @Operation(summary = "审查任务分页")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:query')")
    public CommonResult<PageResult<BidReviewTaskRespVO>> getPage(@Valid BidReviewTaskPageReqVO reqVO) {
        return success(taskService.getTaskPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "审查任务详情（含问题统计与报告状态）")
    @Parameter(name = "id", description = "任务 id", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:query')")
    public CommonResult<BidReviewTaskRespVO> get(@RequestParam("id") Long id) {
        return success(taskService.getTask(id));
    }

    @PostMapping("/create")
    @Operation(summary = "建单（可指定归属人/规则集/招标文件）")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:create')")
    @Idempotent
    public CommonResult<Long> create(@Valid @RequestBody BidReviewTaskCreateReqVO reqVO) {
        return success(taskService.createTaskByAdmin(reqVO));
    }

    @PostMapping("/retry")
    @Operation(summary = "重跑（清空旧问题与报告后重新审查）")
    @Parameter(name = "id", description = "任务 id", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:update')")
    @Idempotent
    public CommonResult<Boolean> retry(@RequestParam("id") Long id) {
        taskService.retryTask(id);
        return success(true);
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消任务")
    @Parameter(name = "id", description = "任务 id", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:update')")
    @Idempotent
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        taskService.cancelTask(id);
        return success(true);
    }

    @GetMapping("/overview")
    @Operation(summary = "审查统计（任务数/结论分布/级别分布/高频问题/AI 失败率）")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:query')")
    public CommonResult<BidReviewOverviewRespVO> overview() {
        return success(taskService.getOverview());
    }
}
