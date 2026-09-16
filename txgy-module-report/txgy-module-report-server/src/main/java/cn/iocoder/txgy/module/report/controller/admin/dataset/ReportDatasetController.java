package cn.iocoder.txgy.module.report.controller.admin.dataset;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.report.controller.admin.dataset.vo.*;
import cn.iocoder.txgy.module.report.service.dataset.ReportDatasetService;
import cn.iocoder.txgy.module.report.service.dataset.ReportTableCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.report.enums.ErrorCodeConstants.DATASET_CODE_REQUIRED;

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

    /**
     * 调用方（积木报表、大屏等）自动追加、且数据集从不声明的分页/排序参数，直接忽略
     */
    private static final Set<String> IGNORED_QUERY_PARAMS = Set.of(
            "pageNo", "pageSize", "current", "size", "pages", "total", "sortField", "sortOrder");

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

    @GetMapping("/simple/catalog")
    @Operation(summary = "获得简单模式白名单目录", description = "返回可选表与列（含中文表头、时间列标记），供不写 SQL 的工作人员选择")
    @PreAuthorize("@ss.hasPermission('report:dataset:create')")
    public CommonResult<List<ReportTableCatalog.TableDef>> getSimpleCatalog() {
        return success(ReportTableCatalog.list());
    }

    @PostMapping("/simple")
    @Operation(summary = "简单模式创建数据集", description = "选表勾字段即可，SQL 由服务端自动生成（自动带租户隔离与行数上限）")
    @PreAuthorize("@ss.hasPermission('report:dataset:create')")
    public CommonResult<Long> createSimpleDataset(@Valid @RequestBody SimpleDatasetReqVO reqVO) {
        return success(datasetService.createSimpleDataset(reqVO));
    }

    @RequestMapping("/api-data")
    @Operation(summary = "积木报表 API 数据集取数",
            description = "返回 {data:[{...}]} 结构，供积木「API 数据集」解析；同时支持 GET（URL 传参）与 POST（JSON body）；租户条件由服务端强制注入")
    @PreAuthorize("@ss.hasPermission('report:dataset:execute')")
    public CommonResult<List<Map<String, Object>>> getApiData(
            @RequestParam(required = false) Map<String, String> queryParams,
            @RequestBody(required = false) ReportDatasetExecuteReqVO reqVO) {
        String code = reqVO != null && StrUtil.isNotBlank(reqVO.getCode())
                ? reqVO.getCode()
                : (queryParams == null ? null : queryParams.get("code"));
        if (StrUtil.isBlank(code)) {
            throw exception(DATASET_CODE_REQUIRED);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        if (reqVO != null && reqVO.getParams() != null) {
            params.putAll(reqVO.getParams());
        }
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                // code 单独取出；忽略空值、前端缓存击穿参数（下划线开头）
                // 以及积木等调用方自动追加的分页/排序参数（数据集从不声明这些名字）
                if ("code".equals(key) || StrUtil.isBlank(value) || key.startsWith("_")
                        || IGNORED_QUERY_PARAMS.contains(key)) {
                    return;
                }
                params.put(key, value);
            });
        }
        return success(datasetService.queryRows(code, params));
    }

}
