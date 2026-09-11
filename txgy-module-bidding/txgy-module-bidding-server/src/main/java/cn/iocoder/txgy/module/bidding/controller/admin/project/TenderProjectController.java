package cn.iocoder.txgy.module.bidding.controller.admin.project;

// [规范依据]《招投标模块开发规范》§4.2 + 权限点复用（招标项目作为项目子档共用 bidding:project:*，避免菜单膨胀，见 sql/history/2026-09-10-project-domain.sql 注释）
// [业务目标] 招标项目接口：分页/详情/按编号/登记/删除 + **招标项目维度全景追溯**（追溯主入口）
// [为什么] tender_project_code 是公告（内部/外部）、备案、关注（TENDER_PROJECT 维度）共同的关联键，
//           追溯入口放在此处即"一个招标项目拉起全部信息"，前端详情页只需一次请求

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

@Tag(name = "管理后台 - bidding 招标项目")
@RestController
@RequestMapping("/bidding/tender-project")
@Validated
public class TenderProjectController {

    @Resource
    private ProjectService projectService;
    @Resource
    private ProjectTraceService projectTraceService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:project:query')")
    @Operation(summary = "招标项目分页")
    public CommonResult<PageResult<TenderProjectRespVO>> page(@Valid TenderProjectPageReqVO reqVO) {
        return success(projectService.pageTenderProject(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:project:query')")
    @Operation(summary = "招标项目详情")
    public CommonResult<TenderProjectRespVO> get(@RequestParam("id") Long id) {
        return success(projectService.getTenderProject(id));
    }

    @GetMapping("/get-by-code")
    @PreAuthorize("@ss.hasPermission('bidding:project:query')")
    @Operation(summary = "按招标项目编号查询（追溯联动；不存在返回 null）")
    public CommonResult<TenderProjectRespVO> getByCode(@RequestParam("tenderProjectCode") String tenderProjectCode) {
        return success(projectService.getTenderProjectByCode(tenderProjectCode));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:project:create')")
    @Operation(summary = "招标项目登记/修改（编号租户内唯一）")
    @Idempotent
    public CommonResult<Long> save(@Valid @RequestBody TenderProjectSaveReqVO reqVO) {
        return success(projectService.saveTenderProject(reqVO));
    }

    @PostMapping("/delete")
    @PreAuthorize("@ss.hasPermission('bidding:project:update')")
    @Operation(summary = "招标项目删除（存在标段时拒绝）")
    @Idempotent
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        projectService.deleteTenderProject(id);
        return success(true);
    }

    @GetMapping("/trace")
    @PreAuthorize("@ss.hasPermission('bidding:project:query')")
    @Operation(summary = "招标项目全景追溯（招标项目 → 项目/标段/公告/外部公告/备案/关注/主体/统计）")
    public CommonResult<ProjectTraceRespVO> trace(@RequestParam("tenderProjectCode") String tenderProjectCode) {
        return success(projectTraceService.traceByTenderProject(tenderProjectCode));
    }
}
