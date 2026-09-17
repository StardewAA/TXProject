package cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair.vo.*;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolrepair.MesTmToolRepairDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolrepair.MesTmToolRepairLineDO;
import cn.iocoder.txgy.module.mes.service.tm.toolrepair.MesTmToolRepairService;
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

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.apilog.core.enums.OperateTypeEnum.EXPORT;

@Tag(name = "管理后台 - MES 工具维修单")
@RestController
@RequestMapping("/mes/tm/tool-repair")
@Validated
public class MesTmToolRepairController {

    @Resource
    private MesTmToolRepairService toolRepairService;

    @PostMapping("/create")
    @Operation(summary = "创建工具维修单")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:create')")
    public CommonResult<Long> createToolRepair(@Valid @RequestBody MesTmToolRepairSaveReqVO createReqVO) {
        return success(toolRepairService.createToolRepair(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工具维修单")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:update')")
    public CommonResult<Boolean> updateToolRepair(@Valid @RequestBody MesTmToolRepairSaveReqVO updateReqVO) {
        toolRepairService.updateToolRepair(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工具维修单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:delete')")
    public CommonResult<Boolean> deleteToolRepair(@RequestParam("id") Long id) {
        toolRepairService.deleteToolRepair(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工具维修单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:query')")
    public CommonResult<MesTmToolRepairRespVO> getToolRepair(@RequestParam("id") Long id) {
        MesTmToolRepairDO repair = toolRepairService.getToolRepair(id);
        return success(BeanUtils.toBean(repair, MesTmToolRepairRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得工具维修单分页")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:query')")
    public CommonResult<PageResult<MesTmToolRepairRespVO>> getToolRepairPage(@Valid MesTmToolRepairPageReqVO pageReqVO) {
        PageResult<MesTmToolRepairDO> pageResult = toolRepairService.getToolRepairPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, MesTmToolRepairRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出工具维修单 Excel")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportToolRepairExcel(@Valid MesTmToolRepairPageReqVO pageReqVO,
                                      HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<MesTmToolRepairDO> list = toolRepairService.getToolRepairPage(pageReqVO).getList();
        ExcelUtils.write(response, "工具维修单.xls", "数据", MesTmToolRepairRespVO.class,
                BeanUtils.toBean(list, MesTmToolRepairRespVO.class));
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认工具维修单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:update')")
    public CommonResult<Boolean> confirmToolRepair(@RequestParam("id") Long id) {
        toolRepairService.confirmToolRepair(id);
        return success(true);
    }

    @PutMapping("/finish")
    @Operation(summary = "完成工具维修单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:update')")
    public CommonResult<Boolean> finishToolRepair(@RequestParam("id") Long id) {
        toolRepairService.finishToolRepair(id);
        return success(true);
    }

    // ========== 行 ==========

    @PostMapping("/line/create")
    @Operation(summary = "创建工具维修单行")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:create')")
    public CommonResult<Long> createToolRepairLine(@Valid @RequestBody MesTmToolRepairLineSaveReqVO createReqVO) {
        return success(toolRepairService.createToolRepairLine(createReqVO));
    }

    @PutMapping("/line/update")
    @Operation(summary = "更新工具维修单行")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:update')")
    public CommonResult<Boolean> updateToolRepairLine(@Valid @RequestBody MesTmToolRepairLineSaveReqVO updateReqVO) {
        toolRepairService.updateToolRepairLine(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/line/delete")
    @Operation(summary = "删除工具维修单行")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:delete')")
    public CommonResult<Boolean> deleteToolRepairLine(@RequestParam("id") Long id) {
        toolRepairService.deleteToolRepairLine(id);
        return success(true);
    }

    @GetMapping("/line/list-by-repair")
    @Operation(summary = "获取工具维修单行列表")
    @Parameter(name = "repairId", description = "维修单ID", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-repair:query')")
    public CommonResult<List<MesTmToolRepairLineDO>> getToolRepairLineListByRepairId(@RequestParam("repairId") Long repairId) {
        return success(toolRepairService.getToolRepairLineListByRepairId(repairId));
    }
}
