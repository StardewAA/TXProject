package cn.iocoder.txgy.module.training.controller.admin.analytics;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.admin.analytics.vo.AnalyticsDashboardRespVO;
import cn.iocoder.txgy.module.training.service.analytics.TrainingAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 数据统计与档案归档")
@RestController
@RequestMapping("/training/analytics")
@Validated
public class TrainingAnalyticsController {

    @Resource
    private TrainingAnalyticsService analyticsService;

    @GetMapping("/dashboard")
    @Operation(summary = "数据统计看板（课程/资源/互动规模与趋势）")
    @PreAuthorize("@ss.hasPermission('training:analytics:query')")
    public CommonResult<AnalyticsDashboardRespVO> getDashboard() {
        return success(analyticsService.getDashboard());
    }

}
