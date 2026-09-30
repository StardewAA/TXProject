package cn.iocoder.txgy.module.bidding.controller.admin.project;

// [规范依据]《招投标模块开发规范》§4.2 + 权限点 bidding:bid-section:query/create/update（sql/history/2026-09-10-project-domain.sql）
// [业务目标] 标段（包）接口：分页/详情/按编号/登记 + 标段维度追溯（标段 → 所属招标项目 → 项目）
// [为什么] 标段是投标参与的最小单元：截止时间查询（/notice/exchange/deadline）与公告 BID_SECTION_CODES 均以标段编号定位，
//           提供标段维度追溯可让"某标段有哪些公告/谁在关注"直接可查

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.project.vo.*;
import cn.iocoder.txgy.module.bidding.service.project.ProjectService;
import cn.iocoder.txgy.module.bidding.service.project.ProjectTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - bidding 标段（包）")
@RestController
@RequestMapping("/bidding/bid-section")
@Validated
public class BidSectionController {

    @Resource
    private ProjectService projectService;
    @Resource
    private ProjectTraceService projectTraceService;

    @GetMapping("/page")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "标段（包）分页")
    public CommonResult<PageResult<BidSectionRespVO>> page(@Valid BidSectionPageReqVO reqVO) {
        return success(projectService.pageBidSection(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:bid-section:query')")
    @Operation(summary = "标段（包）详情")
    public CommonResult<BidSectionRespVO> get(@RequestParam("id") Long id) {
        return success(projectService.getBidSection(id));
    }

    @GetMapping("/get-by-code")
    @PreAuthorize("@ss.hasPermission('bidding:bid-section:query')")
    @Operation(summary = "按标段（包）编号查询（不存在返回 null）")
    public CommonResult<BidSectionRespVO> getByCode(@RequestParam("bidSectionCode") String bidSectionCode) {
        return success(projectService.getBidSectionByCode(bidSectionCode));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:bid-section:create')")
    @Operation(summary = "标段（包）登记/修改（须归属已登记招标项目）")
    @Idempotent
    public CommonResult<Long> save(@Valid @RequestBody BidSectionSaveReqVO reqVO) {
        return success(projectService.saveBidSection(reqVO));
    }

    @GetMapping("/trace")
    @PreAuthorize("@ss.hasPermission('bidding:bid-section:query')")
    @Operation(summary = "标段全景追溯（标段 → 招标项目 → 项目 + 公告/外部公告/备案/关注/主体）")
    public CommonResult<ProjectTraceRespVO> trace(@RequestParam("bidSectionCode") String bidSectionCode) {
        return success(projectTraceService.traceByBidSection(bidSectionCode));
    }
}
