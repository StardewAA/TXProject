package cn.iocoder.txgy.module.bidding.controller.admin.project;

// [规范依据]《招投标模块开发规范》§4.2（Controller 只做参数校验/权限/返回值包装；业务在 service）
//           + 权限点登记（sql/history/2026-09-10-project-domain.sql：bidding:project:query/create/update）
// [业务目标] 项目台账接口：分页/详情/按编号查询/登记/删除，并暴露项目维度全景追溯入口
// [为什么] 项目是全链路追溯的根锚点（公告、外部公告、备案、关注、主体均以 project_code 关联），接口需与追溯入口同址便于前端一次调用

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.project.vo.*;
import cn.iocoder.txgy.module.bidding.service.project.ProjectService;
import cn.iocoder.txgy.module.bidding.service.project.ProjectTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - bidding 项目台账")
@RestController
@RequestMapping("/bidding/project")
@Validated
public class ProjectController {

    @Resource
    private ProjectService projectService;
    @Resource
    private ProjectTraceService projectTraceService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:project:query')")
    @Operation(summary = "项目分页")
    public CommonResult<PageResult<ProjectRespVO>> page(@Valid ProjectPageReqVO reqVO) {
        return success(projectService.pageProject(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:project:query')")
    @Operation(summary = "项目详情（管理端可含政务公开字段）",
            description = "includeGovernment=true 时 fields 返回全量非空字段（含政务公开级）——"
                    + "管理端台账是**填报方**界面，须能看到/修改自己录入的政务字段；"
                    + "缺省 false = 对外口径（@PublicLevel(SOCIAL) 裁剪），门户前台复用同一 URL，行为不变。")
    @Parameter(name = "includeGovernment", description = "是否包含政务公开字段（缺省 false=对外口径）", example = "true")
    public CommonResult<ProjectRespVO> get(@RequestParam("id") Long id,
                                           @RequestParam(value = "includeGovernment", required = false, defaultValue = "false")
                                           Boolean includeGovernment) {
        return success(projectService.getProject(id, Boolean.TRUE.equals(includeGovernment)));
    }

    @GetMapping("/get-by-code")
    @PreAuthorize("@ss.hasPermission('bidding:project:query')")
    @Operation(summary = "按项目编号查询（追溯联动；不存在返回 null）",
            description = "includeGovernment 语义同 /get（缺省 false=对外口径）")
    @Parameter(name = "includeGovernment", description = "是否包含政务公开字段（缺省 false=对外口径）", example = "true")
    public CommonResult<ProjectRespVO> getByCode(@RequestParam("projectCode") String projectCode,
                                                 @RequestParam(value = "includeGovernment", required = false, defaultValue = "false")
                                                 Boolean includeGovernment) {
        return success(projectService.getProjectByCode(projectCode, Boolean.TRUE.equals(includeGovernment)));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:project:create')")
    @Operation(summary = "项目登记/修改（编号租户内唯一，重复提交幂等 upsert）")
    @Idempotent
    public CommonResult<Long> save(@Valid @RequestBody ProjectSaveReqVO reqVO) {
        return success(projectService.saveProject(reqVO));
    }

    @PostMapping("/delete")
    @PreAuthorize("@ss.hasPermission('bidding:project:update')")
    @Operation(summary = "项目删除（存在招标项目时拒绝，保追溯链完整）")
    @Parameter(name = "id", description = "项目 ID", required = true)
    @Idempotent
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        projectService.deleteProject(id);
        return success(true);
    }

    @GetMapping("/trace")
    @PreAuthorize("@ss.hasPermission('bidding:project:query')")
    @Operation(summary = "项目全景追溯（项目 → 招标项目/标段/公告/外部公告/备案/关注/主体/统计）",
            description = "includeGovernment 语义同 /get（缺省 false=对外口径）；追溯内嵌的项目/标段同口径裁剪")
    @Parameter(name = "includeGovernment", description = "是否包含政务公开字段（缺省 false=对外口径）", example = "true")
    public CommonResult<ProjectTraceRespVO> trace(@RequestParam("projectCode") String projectCode,
                                                  @RequestParam(value = "includeGovernment", required = false, defaultValue = "false")
                                                  Boolean includeGovernment) {
        return success(projectTraceService.traceByProject(projectCode, Boolean.TRUE.equals(includeGovernment)));
    }
}
