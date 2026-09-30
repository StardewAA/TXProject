package cn.iocoder.txgy.module.bidding.controller.admin.bidreview;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportDocRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportPageRespVO;
import cn.iocoder.txgy.module.bidding.service.bidreview.BidReviewReportService;
import cn.iocoder.txgy.module.bidding.service.bidreview.BidReviewReportDocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 智慧标书审查 · 管理端报告接口。
 *
 * <p>方案依据：§5.2（report/get、report/export）。PDF/Word 渲染属 P1（方案 §8 P1）。
 */
@Tag(name = "管理后台 - 智慧标书审查报告")
@RestController
@RequestMapping("/bidding/bid-review/report")
@Validated
public class BidReviewReportController {

    @Resource
    private BidReviewReportService reportService;
    @Resource
    private BidReviewReportDocService reportDocService;

    @GetMapping("/get")
    @Operation(summary = "按任务取审查报告")
    @Parameter(name = "taskId", description = "任务 id", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:query')")
    public CommonResult<BidReviewReportRespVO> get(@RequestParam("taskId") Long taskId) {
        return success(reportService.getReportByTaskId(taskId, false));
    }

    @GetMapping("/export")
    @Operation(summary = "导出报告正文（HTML，供打印/另存）")
    @Parameter(name = "taskId", description = "任务 id", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:query')")
    public CommonResult<String> export(@RequestParam("taskId") Long taskId) {
        return success(reportService.exportHtml(taskId, false));
    }

    @GetMapping("/page")
    @Operation(summary = "报告台账分页（报告与统计页下方的报告台账）")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:query')")
    public CommonResult<PageResult<BidReviewReportPageRespVO>> getPage(@Valid BidReviewReportPageReqVO reqVO) {
        return success(reportService.getReportPage(reqVO));
    }

    @GetMapping("/doc")
    @Operation(summary = "生成/获取审查报告 Word 文档（可下载）")
    @Parameter(name = "taskId", description = "任务 id", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:query')")
    public CommonResult<BidReviewReportDocRespVO> doc(@RequestParam("taskId") Long taskId) {
        return success(reportDocService.getOrCreateDocx(taskId, false));
    }
}
