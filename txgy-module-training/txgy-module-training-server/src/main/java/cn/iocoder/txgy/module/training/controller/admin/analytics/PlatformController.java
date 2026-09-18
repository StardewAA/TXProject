package cn.iocoder.txgy.module.training.controller.admin.analytics;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.service.analytics.PlatformDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 95 全平台看板与治理（仅超级管理员）")
@RestController
@RequestMapping("/training/analytics/platform")
@Validated
public class PlatformController {

    @Resource
    private PlatformDashboardService platformDashboardService;

    @GetMapping("/dashboard")
    @Operation(summary = "全平台大数据看板指标")
    @PreAuthorize("@ss.hasPermission('training:analytics:platform')")
    public CommonResult<Map<String, Object>> getPlatformDashboard() {
        // TODO[模块九-集团视角] 跨租户汇总查询需 TenantUtils.execute 显式控制
        return success(platformDashboardService.getPlatformDashboard());
    }

    @GetMapping("/export")
    @Operation(summary = "导出平台报表")
    @PreAuthorize("@ss.hasPermission('training:analytics:platform:export')")
    public CommonResult<String> exportPlatformReport() {
        return success(platformDashboardService.exportPlatformReport());
    }

    @GetMapping("/tenant-isolation-config")
    @Operation(summary = "租户与企业数据隔离配置")
    @PreAuthorize("@ss.hasPermission('training:analytics:tenant-isolation:update')")
    public CommonResult<Map<String, Object>> getTenantIsolationConfig() {
        return success(platformDashboardService.getTenantIsolationConfig());
    }

}
