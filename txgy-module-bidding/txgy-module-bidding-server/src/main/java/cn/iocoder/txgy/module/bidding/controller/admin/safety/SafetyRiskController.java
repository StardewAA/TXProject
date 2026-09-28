package cn.iocoder.txgy.module.bidding.controller.admin.safety;

// [规范依据]契约《后端变更请求-电子证照域e2e暴露问题-2026-09-21》P1 方案 A（推荐）：
//   前端已按 /bidding/safety-risk/* 与 /bidding/risk-rule/* 接线（api/bidding/safetyRisk，静默空态降级），
//   后端同能力此前只挂在 /bidding/safety-permit/risk/*；本控制器补别名路径，前端零改动即可用。
// [业务目标] 安许风险条目工单：分页/详情/大盘/导出/指派/处理/扫描/一键预警（复用既有 SafetyRiskItemService）。
// [为什么] 不改既有 /safety-permit/risk/* 路径（已在用，动它会破坏既有调用方），只增别名；
//   权限码沿用已注册的 bidding:safety-risk:{query,assign,handle,alert,scan}（system_menu 已登记，无需新码）。

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemAlertBatchReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemAssignReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemExportVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemHandleReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskItemStatsRespVO;
import cn.iocoder.txgy.module.bidding.service.safety.SafetyPermitRiskService;
import cn.iocoder.txgy.module.bidding.service.safety.SafetyRiskItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "管理后台 - 安许风险条目（契约 e2e P1 方案A 别名路径）")
@RestController
@RequestMapping("/bidding/safety-risk")
@Validated
public class SafetyRiskController {

    @Resource
    private SafetyRiskItemService safetyRiskItemService;

    @Resource
    private SafetyPermitRiskService safetyPermitRiskService;

    @GetMapping("/page")
    @Operation(summary = "风险条目分页（等级/状态/责任人/关键字/企业过滤）")
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:query')")
    public CommonResult<PageResult<SafetyRiskItemRespVO>> page(@Valid SafetyRiskItemPageReqVO reqVO) {
        return CommonResult.success(safetyRiskItemService.pageRiskItem(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "风险条目详情")
    @Parameter(name = "id", description = "工单主键", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:query')")
    public CommonResult<SafetyRiskItemRespVO> get(@RequestParam("id") Long id) {
        return CommonResult.success(safetyRiskItemService.getRiskItem(id));
    }

    @GetMapping("/stats")
    @Operation(summary = "风险大盘（high/medium/low/handled/total；entId 预留，当前统计本租户全量）")
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:query')")
    public CommonResult<SafetyRiskItemStatsRespVO> stats(
            @RequestParam(value = "entId", required = false) Long entId) {
        return CommonResult.success(safetyRiskItemService.stats());
    }

    @GetMapping("/export")
    @Operation(summary = "导出风险报告（范围=当前筛选，xlsx）")
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:query')")
    public void export(@Valid SafetyRiskItemPageReqVO reqVO, HttpServletResponse response) throws IOException {
        List<SafetyRiskItemExportVO> list = safetyRiskItemService.exportRiskItems(reqVO);
        ExcelUtils.write(response, "安许风险条目.xls", "风险条目", SafetyRiskItemExportVO.class, list);
    }

    @PostMapping("/assign")
    @Operation(summary = "指派责任人（乐观锁 CAS，终态不可指派）")
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:assign')")
    @Idempotent
    public CommonResult<Boolean> assign(@Valid @RequestBody SafetyRiskItemAssignReqVO reqVO) {
        safetyRiskItemService.assign(reqVO, SecurityFrameworkUtils.getLoginUserId());
        return CommonResult.success(true);
    }

    @PostMapping("/handle")
    @Operation(summary = "处理风险（状态流转，乐观锁 CAS；入参 status，兼容 targetStatus）")
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:handle')")
    @Idempotent
    public CommonResult<Boolean> handle(@Valid @RequestBody SafetyRiskItemHandleReqVO reqVO) {
        safetyRiskItemService.handle(reqVO, SecurityFrameworkUtils.getLoginUserId());
        return CommonResult.success(true);
    }

    @PostMapping("/scan")
    @Operation(summary = "重新扫描风险（全量重算安许 A/B/C 并按档位生成/点亮/闭环工单）；返回本次评估证数")
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:scan')")
    @Idempotent
    public CommonResult<Integer> scan() {
        // 口径：与既有 /safety-permit/risk/recheck（单证试算）不同——本端点做全量重算（evaluateAll 落库路径，
        // 内部 evaluateOne 完成后经 syncFromEvaluation 生成/点亮/闭环工单，与 Job 同一条链路）。
        return CommonResult.success(safetyPermitRiskService.evaluateAll(LocalDate.now(), false));
    }

    @PostMapping("/alert/batch")
    @Operation(summary = "一键预警（对所选风险条目批量派发 ALERT 通知，同日同条幂等）；返回实际派发数")
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:alert')")
    @Idempotent
    public CommonResult<Integer> alertBatch(@Valid @RequestBody SafetyRiskItemAlertBatchReqVO reqVO) {
        return CommonResult.success(safetyRiskItemService.alertBatch(reqVO.getIds(),
                SecurityFrameworkUtils.getLoginUserId()));
    }
}
