package cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal;
// 规范依据：设计文档 v0.6 §三（批次）；《招投标模块开发规范》§4.4

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlJobPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlJobRespVO;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.CrawlJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 外部公告采集批次（调度/人工跑批汇总，管理面只读）。
 */
@Tag(name = "管理后台 - bidding 外部公告采集批次")
@RestController
@RequestMapping("/bidding/crawl-job")
@Validated
public class CrawlJobController {

    @Resource
    private CrawlJobService crawlJobService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-job:query')")
    @Operation(summary = "采集批次分页")
    public CommonResult<PageResult<CrawlJobRespVO>> page(@Valid CrawlJobPageReqVO reqVO) {
        return success(crawlJobService.getJobPage(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:crawl-job:query')")
    @Operation(summary = "采集批次详情")
    public CommonResult<CrawlJobRespVO> get(@RequestParam("id") Long id) {
        return success(crawlJobService.getJob(id));
    }

}
