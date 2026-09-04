package cn.iocoder.txgy.module.mes.controller.admin.pro.project;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectStatisticsRespVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectDO;
import cn.iocoder.txgy.module.mes.service.pro.project.MesProProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "管理后台 - MES 生产管理-项目管理")
@RestController
@RequestMapping("/mes/pro/project")
@Validated
public class MesProProjectController {

    @Resource
    private MesProProjectService projectService;

    @PostMapping("/create")
    @Operation(summary = "创建项目")
    @PreAuthorize("@ss.hasPermission('mes:pro-project:create')")
    public CommonResult<Long> createProject(@Valid @RequestBody MesProProjectSaveReqVO createReqVO) {
        return success(projectService.createProject(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目")
    @PreAuthorize("@ss.hasPermission('mes:pro-project:update')")
    public CommonResult<Boolean> updateProject(@Valid @RequestBody MesProProjectSaveReqVO updateReqVO) {
        projectService.updateProject(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:pro-project:delete')")
    public CommonResult<Boolean> deleteProject(@RequestParam("id") Long id) {
        projectService.deleteProject(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得项目")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mes:pro-project:query')")
    public CommonResult<MesProProjectRespVO> getProject(@RequestParam("id") Long id) {
        MesProProjectDO project = projectService.getProject(id);
        MesProProjectRespVO respVO = BeanUtils.toBean(project, MesProProjectRespVO.class);
        if (respVO != null) {
            fillWorkOrderCount(respVO);
        }
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目分页")
    @PreAuthorize("@ss.hasPermission('mes:pro-project:query')")
    public CommonResult<PageResult<MesProProjectRespVO>> getProjectPage(@Valid MesProProjectPageReqVO pageReqVO) {
        PageResult<MesProProjectDO> pageResult = projectService.getProjectPage(pageReqVO);
        return success(new PageResult<>(buildRespVOList(pageResult.getList()), pageResult.getTotal()));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出项目 Excel")
    @PreAuthorize("@ss.hasPermission('mes:pro-project:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportProjectExcel(@Valid MesProProjectPageReqVO pageReqVO, HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<MesProProjectDO> list = projectService.getProjectPage(pageReqVO).getList();
        List<MesProProjectRespVO> voList = BeanUtils.toBean(list, MesProProjectRespVO.class);
        ExcelUtils.write(response, "项目管理.xls", "项目数据", MesProProjectRespVO.class, voList);
    }

    @GetMapping("/statistics")
    @Operation(summary = "获得项目的生产进度统计")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mes:pro-project:query')")
    public CommonResult<MesProProjectStatisticsRespVO> getProjectStatistics(
            @RequestParam("projectId") Long projectId) {
        return success(projectService.getProjectStatistics(projectId));
    }

    @PutMapping("/refresh-status")
    @Operation(summary = "按工单进度重算项目状态")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mes:pro-project:update')")
    public CommonResult<Boolean> refreshProjectStatus(@RequestParam("projectId") Long projectId) {
        projectService.refreshProjectStatus(projectId);
        return success(true);
    }

    @PutMapping("/bind-work-orders")
    @Operation(summary = "批量将生产工单挂接到项目")
    @Parameter(name = "projectId", description = "项目编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mes:pro-project:update')")
    public CommonResult<Boolean> bindWorkOrders(@RequestParam("projectId") Long projectId,
                                                @RequestBody List<Long> workOrderIds) {
        projectService.bindWorkOrders(projectId, workOrderIds);
        return success(true);
    }

    @PutMapping("/unbind-work-orders")
    @Operation(summary = "批量解除生产工单的项目关联")
    @PreAuthorize("@ss.hasPermission('mes:pro-project:update')")
    public CommonResult<Boolean> unbindWorkOrders(@RequestBody List<Long> workOrderIds) {
        projectService.unbindWorkOrders(workOrderIds);
        return success(true);
    }

    // ==================== 私有方法 ====================

    /**
     * 填充列表的项目工单统计
     */
    private List<MesProProjectRespVO> buildRespVOList(List<MesProProjectDO> list) {
        Map<Long, MesProProjectStatisticsRespVO> statisticsMap =
                projectService.getProjectStatisticsMap(convertSet(list, MesProProjectDO::getId));
        return BeanUtils.toBean(list, MesProProjectRespVO.class, project -> {
            MesProProjectStatisticsRespVO statistics = statisticsMap.get(project.getId());
            project.setWorkOrderCount(statistics != null ? statistics.getWorkOrderTotal() : 0);
            project.setFinishedWorkOrderCount(statistics != null ? statistics.getFinishedCount() : 0);
        });
    }

    /**
     * 填充单条项目的工单统计
     */
    private void fillWorkOrderCount(MesProProjectRespVO respVO) {
        MesProProjectStatisticsRespVO statistics = projectService.getProjectStatistics(respVO.getId());
        respVO.setWorkOrderCount(statistics.getWorkOrderTotal() != null
                ? statistics.getWorkOrderTotal() : 0);
        respVO.setFinishedWorkOrderCount(statistics.getFinishedCount() != null
                ? statistics.getFinishedCount() : 0);
    }

}
