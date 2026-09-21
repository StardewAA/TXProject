package cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo.*;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.workstation.MesMdWorkstationDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolissue.MesTmToolIssueDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolissue.MesTmToolIssueLineDO;
import cn.iocoder.txgy.module.mes.service.md.workstation.MesMdWorkstationService;
import cn.iocoder.txgy.module.mes.service.tm.toolissue.MesTmToolIssueService;
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

@Tag(name = "管理后台 - MES 工具领用单")
@RestController
@RequestMapping("/mes/tm/tool-issue")
@Validated
public class MesTmToolIssueController {

    @Resource
    private MesTmToolIssueService toolIssueService;
    @Resource
    private MesMdWorkstationService workstationService;

    @PostMapping("/create")
    @Operation(summary = "创建工具领用单")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:create')")
    public CommonResult<Long> createToolIssue(@Valid @RequestBody MesTmToolIssueSaveReqVO createReqVO) {
        return success(toolIssueService.createToolIssue(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工具领用单")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:update')")
    public CommonResult<Boolean> updateToolIssue(@Valid @RequestBody MesTmToolIssueSaveReqVO updateReqVO) {
        toolIssueService.updateToolIssue(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工具领用单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:delete')")
    public CommonResult<Boolean> deleteToolIssue(@RequestParam("id") Long id) {
        toolIssueService.deleteToolIssue(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工具领用单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:query')")
    public CommonResult<MesTmToolIssueRespVO> getToolIssue(@RequestParam("id") Long id) {
        MesTmToolIssueDO issue = toolIssueService.getToolIssue(id);
        if (issue == null) {
            return success(null);
        }
        MesTmToolIssueRespVO respVO = BeanUtils.toBean(issue, MesTmToolIssueRespVO.class);
        if (issue.getWorkstationId() != null) {
            MesMdWorkstationDO ws = workstationService.getWorkstationMap(List.of(issue.getWorkstationId()))
                    .get(issue.getWorkstationId());
            if (ws != null) {
                respVO.setWorkstationName(ws.getName());
            }
        }
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得工具领用单分页")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:query')")
    public CommonResult<PageResult<MesTmToolIssueRespVO>> getToolIssuePage(@Valid MesTmToolIssuePageReqVO pageReqVO) {
        PageResult<MesTmToolIssueDO> pageResult = toolIssueService.getToolIssuePage(pageReqVO);
        PageResult<MesTmToolIssueRespVO> voPage = BeanUtils.toBean(pageResult, MesTmToolIssueRespVO.class);
        // 批量拼接工位名称
        List<Long> wsIds = voPage.getList().stream()
                .filter(v -> v.getWorkstationId() != null)
                .map(MesTmToolIssueRespVO::getWorkstationId).distinct().toList();
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
    @Operation(summary = "导出工具领用单 Excel")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportToolIssueExcel(@Valid MesTmToolIssuePageReqVO pageReqVO,
                                     HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<MesTmToolIssueDO> list = toolIssueService.getToolIssuePage(pageReqVO).getList();
        ExcelUtils.write(response, "工具领用单.xls", "数据", MesTmToolIssueRespVO.class,
                BeanUtils.toBean(list, MesTmToolIssueRespVO.class));
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认工具领用单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:update')")
    public CommonResult<Boolean> confirmToolIssue(@RequestParam("id") Long id) {
        toolIssueService.confirmToolIssue(id);
        return success(true);
    }

    // ========== 行 ==========

    @PostMapping("/line/create")
    @Operation(summary = "创建工具领用单行")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:create')")
    public CommonResult<Long> createToolIssueLine(@Valid @RequestBody MesTmToolIssueLineSaveReqVO createReqVO) {
        return success(toolIssueService.createToolIssueLine(createReqVO));
    }

    @PutMapping("/line/update")
    @Operation(summary = "更新工具领用单行")
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:update')")
    public CommonResult<Boolean> updateToolIssueLine(@Valid @RequestBody MesTmToolIssueLineSaveReqVO updateReqVO) {
        toolIssueService.updateToolIssueLine(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/line/delete")
    @Operation(summary = "删除工具领用单行")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:delete')")
    public CommonResult<Boolean> deleteToolIssueLine(@RequestParam("id") Long id) {
        toolIssueService.deleteToolIssueLine(id);
        return success(true);
    }

    @GetMapping("/line/list-by-issue")
    @Operation(summary = "获取工具领用单行列表")
    @Parameter(name = "issueId", description = "领用单ID", required = true)
    @PreAuthorize("@ss.hasPermission('mes:tm-tool-issue:query')")
    public CommonResult<List<MesTmToolIssueLineDO>> getToolIssueLineListByIssueId(@RequestParam("issueId") Long issueId) {
        return success(toolIssueService.getToolIssueLineListByIssueId(issueId));
    }
}
