package cn.iocoder.txgy.module.mes.controller.admin.home;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeAndonAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeCheckMaintenRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeDefectAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeMachineryStatusRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeProductionTrendRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeQcPassRateRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeRepairAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeSummaryRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeWorkOrderStatusRespVO;
import cn.iocoder.txgy.module.mes.service.home.MesHomeStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - MES 首页统计")
@RestController
@RequestMapping("/mes/home-statistics")
@Validated
public class MesHomeStatisticsController {

    @Resource
    private MesHomeStatisticsService homeStatisticsService;

    @GetMapping("/summary")
    @Operation(summary = "获得首页汇总统计")
    @PreAuthorize("@ss.hasPermission('mes:home:query')")
    public CommonResult<MesHomeSummaryRespVO> getHomeSummary() {
        return success(homeStatisticsService.getHomeSummary());
    }

    @GetMapping("/work-order-status")
    @Operation(summary = "获得工单状态分布")
    @PreAuthorize("@ss.hasPermission('mes:home:query')")
    public CommonResult<List<MesHomeWorkOrderStatusRespVO>> getWorkOrderStatusDistribution() {
        return success(homeStatisticsService.getWorkOrderStatusDistribution());
    }

    @GetMapping("/production-trend")
    @Operation(summary = "获得生产趋势")
    @Parameter(name = "days", description = "天数", example = "7")
    @PreAuthorize("@ss.hasPermission('mes:home:query')")
    public CommonResult<List<MesHomeProductionTrendRespVO>> getProductionTrend(
            @RequestParam(value = "days", defaultValue = "7") @Min(1) @Max(90) Integer days) {
        return success(homeStatisticsService.getProductionTrend(days));
    }

    @GetMapping("/machinery-status")
    @Operation(summary = "获得设备状态分布")
    @PreAuthorize("@ss.hasPermission('mes:home:query')")
    public CommonResult<List<MesHomeMachineryStatusRespVO>> getMachineryStatusDistribution() {
        return success(homeStatisticsService.getMachineryStatusDistribution());
    }

    @GetMapping("/qc-pass-rate")
    @Operation(summary = "获得质检合格率")
    @Parameter(name = "days", description = "天数", example = "30")
    @PreAuthorize("@ss.hasPermission('mes:home:query')")
    public CommonResult<List<MesHomeQcPassRateRespVO>> getQcPassRate(
            @RequestParam(value = "days", defaultValue = "30") @Min(1) @Max(365) Integer days) {
        return success(homeStatisticsService.getQcPassRate(days));
    }

    @GetMapping("/andon-analysis")
    @Operation(summary = "获得安灯呼叫分析")
    @Parameter(name = "days", description = "天数", example = "30")
    @PreAuthorize("@ss.hasPermission('mes:home:query')")
    public CommonResult<MesHomeAndonAnalysisRespVO> getAndonAnalysis(
            @RequestParam(value = "days", defaultValue = "30") @Min(1) @Max(365) Integer days) {
        return success(homeStatisticsService.getAndonAnalysis(days));
    }

    @GetMapping("/repair-analysis")
    @Operation(summary = "获得维修工单分析")
    @Parameter(name = "days", description = "天数", example = "30")
    @PreAuthorize("@ss.hasPermission('mes:home:query')")
    public CommonResult<MesHomeRepairAnalysisRespVO> getRepairAnalysis(
            @RequestParam(value = "days", defaultValue = "30") @Min(1) @Max(365) Integer days) {
        return success(homeStatisticsService.getRepairAnalysis(days));
    }

    @GetMapping("/check-mainten")
    @Operation(summary = "获得点检保养执行情况")
    @Parameter(name = "days", description = "天数", example = "30")
    @PreAuthorize("@ss.hasPermission('mes:home:query')")
    public CommonResult<MesHomeCheckMaintenRespVO> getCheckMainten(
            @RequestParam(value = "days", defaultValue = "30") @Min(1) @Max(365) Integer days) {
        return success(homeStatisticsService.getCheckMainten(days));
    }

    @GetMapping("/defect-analysis")
    @Operation(summary = "获得不良缺陷分析")
    @Parameter(name = "days", description = "天数", example = "30")
    @PreAuthorize("@ss.hasPermission('mes:home:query')")
    public CommonResult<MesHomeDefectAnalysisRespVO> getDefectAnalysis(
            @RequestParam(value = "days", defaultValue = "30") @Min(1) @Max(365) Integer days) {
        return success(homeStatisticsService.getDefectAnalysis(days));
    }

}
