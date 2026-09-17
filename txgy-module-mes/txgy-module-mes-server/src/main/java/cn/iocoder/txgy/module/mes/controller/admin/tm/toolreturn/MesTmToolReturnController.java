package cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo.*;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.workstation.MesMdWorkstationDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolreturn.MesTmToolReturnDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolreturn.MesTmToolReturnLineDO;
import cn.iocoder.txgy.module.mes.service.md.workstation.MesMdWorkstationService;
import cn.iocoder.txgy.module.mes.service.tm.toolreturn.MesTmToolReturnService;
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

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.apilog.core.enums.OperateTypeEnum.EXPORT;

@Tag(name = "管理后台 - MES 工具归还单")
@RestController
@RequestMapping("/mes/tm/tool-return")
@Validated
public class MesTmToolReturnController {

    @Resource
    private MesTmToolReturnService toolReturnService;
    @Resource
    private MesMdWorkstationService workstationService;

    @PostMapping("/create")
    @Operation(summary = "创建工具归还单")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:create')")
    public CommonResult<Long> createToolReturn(@Valid @RequestBody MesTmToolReturnSaveReqVO createReqVO) {
        return success(toolReturnService.createToolReturn(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工具归还单")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:update')")
    public CommonResult<Boolean> updateToolReturn(@Valid @RequestBody MesTmToolReturnSaveReqVO updateReqVO) {
        toolReturnService.updateToolReturn(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工具归还单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:delete')")
    public CommonResult<Boolean> deleteToolReturn(@RequestParam("id") Long id) {
        toolReturnService.deleteToolReturn(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工具归还单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:query')")
    public CommonResult<MesTmToolReturnRespVO> getToolReturn(@RequestParam("id") Long id) {
        MesTmToolReturnDO ret = toolReturnService.getToolReturn(id);
        if (ret == null) {
            return success(null);
        }
        MesTmToolReturnRespVO respVO = BeanUtils.toBean(ret, MesTmToolReturnRespVO.class);
        if (ret.getWorkstationId() != null) {
            MesMdWorkstationDO ws = workstationService.getWorkstationMap(List.of(ret.getWorkstationId()))
                    .get(ret.getWorkstationId());
            if (ws != null) {
                respVO.setWorkstationName(ws.getName());
            }
        }
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得工具归还单分页")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:query')")
    public CommonResult<PageResult<MesTmToolReturnRespVO>> getToolReturnPage(@Valid MesTmToolReturnPageReqVO pageReqVO) {
        PageResult<MesTmToolReturnDO> pageResult = toolReturnService.getToolReturnPage(pageReqVO);
        PageResult<MesTmToolReturnRespVO> voPage = BeanUtils.toBean(pageResult, MesTmToolReturnRespVO.class);
        List<Long> wsIds = voPage.getList().stream()
                .filter(v -> v.getWorkstationId() != null)
                .map(MesTmToolReturnRespVO::getWorkstationId).distinct().toList();
        if (!wsIds.isEmpty()) {
            Map<Long, MesMdWorkstationDO> wsMap = workstationService.getWorkstationMap(wsIds);
            voPage.getList().forEach(v -> {
                MesMdWorkstationDO ws = wsMap.get(v.getWorkstationId());
                if (ws != null) {
                    v.setWorkstationName(ws.getName());
                }
            });
        }
        return success(voPage);
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出工具归还单 Excel")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportToolReturnExcel(@Valid MesTmToolReturnPageReqVO pageReqVO,
                                      HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<MesTmToolReturnDO> list = toolReturnService.getToolReturnPage(pageReqVO).getList();
        ExcelUtils.write(response, "工具归还单.xls", "数据", MesTmToolReturnRespVO.class,
                BeanUtils.toBean(list, MesTmToolReturnRespVO.class));
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认工具归还单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:update')")
    public CommonResult<Boolean> confirmToolReturn(@RequestParam("id") Long id) {
        toolReturnService.confirmToolReturn(id);
        return success(true);
    }

    // ========== 行 ==========

    @PostMapping("/line/create")
    @Operation(summary = "创建工具归还单行")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:create')")
    public CommonResult<Long> createToolReturnLine(@Valid @RequestBody MesTmToolReturnLineSaveReqVO createReqVO) {
        return success(toolReturnService.createToolReturnLine(createReqVO));
    }

    @PutMapping("/line/update")
    @Operation(summary = "更新工具归还单行")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:update')")
    public CommonResult<Boolean> updateToolReturnLine(@Valid @RequestBody MesTmToolReturnLineSaveReqVO updateReqVO) {
        toolReturnService.updateToolReturnLine(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/line/delete")
    @Operation(summary = "删除工具归还单行")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:delete')")
    public CommonResult<Boolean> deleteToolReturnLine(@RequestParam("id") Long id) {
        toolReturnService.deleteToolReturnLine(id);
        return success(true);
    }

    @GetMapping("/line/list-by-return")
    @Operation(summary = "获取工具归还单行列表")
    @Parameter(name = "returnId", description = "归还单ID", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-return:query')")
    public CommonResult<List<MesTmToolReturnLineDO>> getToolReturnLineListByReturnId(@RequestParam("returnId") Long returnId) {
        return success(toolReturnService.getToolReturnLineListByReturnId(returnId));
    }
}
