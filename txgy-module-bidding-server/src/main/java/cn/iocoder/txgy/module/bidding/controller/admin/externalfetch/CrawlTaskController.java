package cn.iocoder.txgy.module.bidding.controller.admin.externalfetch;
// 规范依据：设计文档 v0.6 §三（任务状态机/重试/死信转审核）；《招投标模块开发规范》§4.4/4.5

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlTaskRespVO;
import cn.iocoder.txgy.module.bidding.service.externalfetch.CrawlTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 外部公告采集任务（分布式抓取任务：状态/重试/死信）。
 *
 * <p>人工重试语义：仅 FAILED（死信/转人工审核）可置回 PENDING 开启新一轮尝试——审核处理后放行。
 */
@Tag(name = "管理后台 - bidding 外部公告采集任务")
@RestController
@RequestMapping("/bidding/crawl-task")
@Validated
public class CrawlTaskController {

    @Resource
    private CrawlTaskService crawlTaskService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-task:query')")
    @Operation(summary = "采集任务分页（管理面：批次/源/状态/URL 检索）")
    public CommonResult<PageResult<CrawlTaskRespVO>> page(@Valid CrawlTaskPageReqVO reqVO) {
        return success(crawlTaskService.getTaskPage(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-task:query')")
    @Operation(summary = "采集任务详情")
    public CommonResult<CrawlTaskRespVO> get(@RequestParam("id") Long id) {
        return success(crawlTaskService.getTask(id));
    }

    @PostMapping("/retry")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-task:update')")
    @Idempotent
    @Operation(summary = "人工重试（仅 FAILED 死信任务 → PENDING，attempt 归零开启新一轮）")
    public CommonResult<Boolean> retry(@RequestParam("id") Long id) {
        crawlTaskService.retryTask(id);
        return success(Boolean.TRUE);
    }

}
