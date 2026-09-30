package cn.iocoder.txgy.module.bidding.controller.admin.bidreview;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssuePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssueRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssueReviewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssueStatsRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewIssueDO;
import cn.iocoder.txgy.module.bidding.service.bidreview.BidReviewIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 智慧标书审查 · 管理端问题项接口（复核闭环）。
 *
 * <p>方案依据：§5.2、§7.1（复核抽屉：确认/忽略/补录意见）。
 */
@Tag(name = "管理后台 - 智慧标书审查问题项")
@RestController
@RequestMapping("/bidding/bid-review/issue")
@Validated
public class BidReviewIssueController {

    @Resource
    private BidReviewIssueService issueService;

    @GetMapping("/page")
    @Operation(summary = "问题项分页")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:query')")
    public CommonResult<PageResult<BidReviewIssueRespVO>> getPage(@Valid BidReviewIssuePageReqVO reqVO) {
        PageResult<BidReviewIssueDO> page = issueService.getIssuePage(reqVO);
        PageResult<BidReviewIssueRespVO> result = new PageResult<>(
                issueService.toRespList(page.getList()), page.getTotal());
        return success(result);
    }

    @GetMapping("/stats")
    @Operation(summary = "问题项复核统计（待复核/已确认/已忽略/已整改/证据不足）")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:query')")
    public CommonResult<BidReviewIssueStatsRespVO> getStats() {
        return success(issueService.getIssueStats());
    }

    @PutMapping("/review")
    @Operation(summary = "复核问题项（确认/忽略/标记已整改，并重算结论）")
    @PreAuthorize("@ss.hasPermission('bidding:bid-review:audit')")
    @Idempotent
    public CommonResult<Boolean> review(@Valid @RequestBody BidReviewIssueReviewReqVO reqVO) {
        issueService.reviewIssue(reqVO);
        return success(true);
    }
}
