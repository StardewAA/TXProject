package cn.iocoder.txgy.module.training.controller.admin.analytics;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.service.analytics.EnterpriseReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 94 企业培训报表与档案")
@RestController
@RequestMapping("/training/analytics/enterprise-report")
@Validated
public class EnterpriseReportController {

    @Resource
    private EnterpriseReportService enterpriseReportService;

    @GetMapping("/archive-page")
    @Operation(summary = "本企业全员培训档案清单（含人才等级分布）")
    @PreAuthorize("@ss.hasPermission('training:analytics:enterprise-report:query')")
    public CommonResult<PageResult<?>> getArchivePage() {
        // TODO[模块九-企业档案] 接收分页/筛选参数并透传 Service（租户插件自动隔离）
        return success(enterpriseReportService.getEnterpriseArchivePage(null));
    }

    @GetMapping("/export")
    @Operation(summary = "导出培训统计报表（Excel）")
    @PreAuthorize("@ss.hasPermission('training:analytics:enterprise-report:export')")
    public CommonResult<String> exportReport() {
        return success(enterpriseReportService.exportReport(null));
    }

    @GetMapping("/project-archive")
    @Operation(summary = "项目培训归档报表")
    @Parameter(name = "projectId", description = "项目ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:analytics:enterprise-report:query')")
    public CommonResult<Map<String, Object>> getProjectArchive(@RequestParam("projectId") Long projectId) {
        return success(enterpriseReportService.getProjectArchive(projectId));
    }

}
