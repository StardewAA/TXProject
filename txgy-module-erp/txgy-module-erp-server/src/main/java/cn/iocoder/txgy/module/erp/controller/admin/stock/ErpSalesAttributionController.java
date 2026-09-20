package cn.iocoder.txgy.module.erp.controller.admin.stock;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution.ErpSalesAttributionRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution.ErpSalesPerformanceSummaryRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.attribution.ErpStockSourceBreakdownRespVO;
import cn.iocoder.txgy.module.erp.service.stock.ErpSalesAttributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * ERP 销售归因 Controller
 *
 * <p>直接回答两个业务问题：</p>
 * <ol>
 *     <li><b>仓库里多少是 ERP 销售的</b> —— /source-breakdown：
 *         按来源系统 + 业务类型分组，只有 ERP + 销售出库才算 ERP 销售</li>
 *     <li><b>哪些是 ERP 的什么人销售的</b> —— /performance：
 *         以库存流水的责任人快照归因，按「销售员 × 产品」聚合</li>
 * </ol>
 *
 * <p>归因依据是流水发生时刻的 source_user_id 快照，
 * 因此订单销售员后续变更不会影响历史业绩归属。</p>
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - ERP 销售归因")
@RestController
@RequestMapping("/erp/sales-attribution")
@Validated
public class ErpSalesAttributionController {

    @Resource
    private ErpSalesAttributionService salesAttributionService;

    @GetMapping("/source-breakdown")
    @Operation(summary = "获得出库来源构成",
            description = "回答「仓库里多少是 ERP 销售的」：按来源系统(ERP/商城/MES/手工)与业务类型分组统计")
    @PreAuthorize("@ss.hasPermission('erp:statistics:query')")
    public CommonResult<List<ErpStockSourceBreakdownRespVO>> getSourceBreakdown(
            @Parameter(description = "仓库编号", example = "8")
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @Parameter(description = "开始时间", example = "2026-01-01 00:00:00")
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间", example = "2026-12-31 23:59:59")
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return success(salesAttributionService.getSourceBreakdown(warehouseId, startTime, endTime));
    }

    @GetMapping("/performance")
    @Operation(summary = "获得销售绩效归因",
            description = "回答「哪些是 ERP 的什么人销售的」：按销售员 × 产品聚合出库/退货的数量与金额")
    @PreAuthorize("@ss.hasPermission('erp:statistics:query')")
    public CommonResult<List<ErpSalesAttributionRespVO>> getSalesAttribution(
            @Parameter(description = "销售员编号", example = "80000")
            @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "仓库编号", example = "8")
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @Parameter(description = "开始时间", example = "2026-01-01 00:00:00")
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间", example = "2026-12-31 23:59:59")
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return success(salesAttributionService.getSalesAttribution(userId, warehouseId, startTime, endTime));
    }

    @GetMapping("/performance-summary")
    @Operation(summary = "获得销售绩效汇总（销售员维度）",
            description = "把出入库归因、名下已审批订单金额、经订单归因的回款金额汇总到销售员，并给出应收余额与回款率")
    @PreAuthorize("@ss.hasPermission('erp:statistics:query')")
    public CommonResult<List<ErpSalesPerformanceSummaryRespVO>> getPerformanceSummary(
            @Parameter(description = "销售员编号", example = "80000")
            @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "仓库编号（只影响出入库口径）", example = "8")
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @Parameter(description = "开始时间", example = "2026-01-01 00:00:00")
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间", example = "2026-12-31 23:59:59")
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return success(salesAttributionService.getPerformanceSummary(userId, warehouseId, startTime, endTime));
    }

}
