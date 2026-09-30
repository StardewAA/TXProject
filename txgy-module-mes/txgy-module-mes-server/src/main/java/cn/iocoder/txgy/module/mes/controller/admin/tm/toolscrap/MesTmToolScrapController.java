package cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo.*;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolscrap.MesTmToolScrapDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolscrap.MesTmToolScrapLineDO;
import cn.iocoder.txgy.module.mes.service.tm.toolscrap.MesTmToolScrapService;
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

@Tag(name = "管理后台 - MES 工具报废单")
@RestController
@RequestMapping("/mes/tm/tool-scrap")
@Validated
public class MesTmToolScrapController {

    @Resource
    private MesTmToolScrapService toolScrapService;

    @PostMapping("/create")
    @Operation(summary = "创建工具报废单")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:create')")
    public CommonResult<Long> createToolScrap(@Valid @RequestBody MesTmToolScrapSaveReqVO createReqVO) {
        return success(toolScrapService.createToolScrap(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工具报废单")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:update')")
    public CommonResult<Boolean> updateToolScrap(@Valid @RequestBody MesTmToolScrapSaveReqVO updateReqVO) {
        toolScrapService.updateToolScrap(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工具报废单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:delete')")
    public CommonResult<Boolean> deleteToolScrap(@RequestParam("id") Long id) {
        toolScrapService.deleteToolScrap(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工具报废单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:query')")
    public CommonResult<MesTmToolScrapRespVO> getToolScrap(@RequestParam("id") Long id) {
        MesTmToolScrapDO scrap = toolScrapService.getToolScrap(id);
        return success(BeanUtils.toBean(scrap, MesTmToolScrapRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得工具报废单分页")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:query')")
    public CommonResult<PageResult<MesTmToolScrapRespVO>> getToolScrapPage(@Valid MesTmToolScrapPageReqVO pageReqVO) {
        PageResult<MesTmToolScrapDO> pageResult = toolScrapService.getToolScrapPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, MesTmToolScrapRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出工具报废单 Excel")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportToolScrapExcel(@Valid MesTmToolScrapPageReqVO pageReqVO,
                                     HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<MesTmToolScrapDO> list = toolScrapService.getToolScrapPage(pageReqVO).getList();
        ExcelUtils.write(response, "工具报废单.xls", "数据", MesTmToolScrapRespVO.class,
                BeanUtils.toBean(list, MesTmToolScrapRespVO.class));
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认工具报废单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:update')")
    public CommonResult<Boolean> confirmToolScrap(@RequestParam("id") Long id) {
        toolScrapService.confirmToolScrap(id);
        return success(true);
    }

    // ========== 行 ==========

    @PostMapping("/line/create")
    @Operation(summary = "创建工具报废单行")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:create')")
    public CommonResult<Long> createToolScrapLine(@Valid @RequestBody MesTmToolScrapLineSaveReqVO createReqVO) {
        return success(toolScrapService.createToolScrapLine(createReqVO));
    }

    @PutMapping("/line/update")
    @Operation(summary = "更新工具报废单行")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:update')")
    public CommonResult<Boolean> updateToolScrapLine(@Valid @RequestBody MesTmToolScrapLineSaveReqVO updateReqVO) {
        toolScrapService.updateToolScrapLine(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/line/delete")
    @Operation(summary = "删除工具报废单行")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:delete')")
    public CommonResult<Boolean> deleteToolScrapLine(@RequestParam("id") Long id) {
        toolScrapService.deleteToolScrapLine(id);
        return success(true);
    }

    @GetMapping("/line/list-by-scrap")
    @Operation(summary = "获取工具报废单行列表")
    @Parameter(name = "scrapId", description = "报废单ID", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-scrap:query')")
    public CommonResult<List<MesTmToolScrapLineDO>> getToolScrapLineListByScrapId(@RequestParam("scrapId") Long scrapId) {
        return success(toolScrapService.getToolScrapLineListByScrapId(scrapId));
    }
}
