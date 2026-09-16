package cn.iocoder.txgy.module.report.controller.admin.dataset;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.report.controller.admin.dataset.vo.*;
import cn.iocoder.txgy.module.report.service.dataset.ReportDatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 报表数据集 Controller
 *
 * 报表/大屏取数的唯一入口：SQL 由服务端登记、参数化执行、强制租户与行数约束、全程审计。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - 报表数据集")
@RestController
@RequestMapping("/report/dataset")
@Validated
public class ReportDatasetController {

    @Resource
    private ReportDatasetService datasetService;

    @PostMapping("/create")
    @Operation(summary = "创建数据集")
    @PreAuthorize("@ss.hasPermission('report:dataset:create')")
    public CommonResult<Long> createDataset(@Valid @RequestBody ReportDatasetSaveReqVO reqVO) {
        return success(datasetService.createDataset(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新数据集")
    @PreAuthorize("@ss.hasPermission('report:dataset:update')")
    public CommonResult<Boolean> updateDataset(@Valid @RequestBody ReportDatasetSaveReqVO reqVO) {
        datasetService.updateDataset(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除数据集")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('report:dataset:delete')")
    public CommonResult<Boolean> deleteDataset(@RequestParam("id") Long id) {
        datasetService.deleteDataset(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得数据集")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('report:dataset:query')")
    public CommonResult<ReportDatasetRespVO> getDataset(@RequestParam("id") Long id) {
        return success(datasetService.getDataset(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得数据集分页")
    @PreAuthorize("@ss.hasPermission('report:dataset:query')")
    public CommonResult<PageResult<ReportDatasetRespVO>> getDatasetPage(@Valid ReportDatasetPageReqVO reqVO) {
        return success(datasetService.getDatasetPage(reqVO));
    }

    @GetMapping("/list-enabled")
    @Operation(summary = "获得可用数据集列表（当前租户 + 平台级）", description = "供报表/大屏选择数据源")
    @PreAuthorize("@ss.hasPermission('report:dataset:query')")
    public CommonResult<List<ReportDatasetRespVO>> getEnabledDatasetList() {
        return success(datasetService.getEnabledDatasetList());
    }

    @PostMapping("/execute")
    @Operation(summary = "执行数据集查询", description = "租户条件由服务端强制注入；结果行数超上限会直接报错")
    @PreAuthorize("@ss.hasPermission('report:dataset:execute')")
    public CommonResult<ReportDatasetExecuteRespVO> executeDataset(@Valid @RequestBody ReportDatasetExecuteReqVO reqVO) {
        return success(datasetService.executeDataset(reqVO));
    }

}
