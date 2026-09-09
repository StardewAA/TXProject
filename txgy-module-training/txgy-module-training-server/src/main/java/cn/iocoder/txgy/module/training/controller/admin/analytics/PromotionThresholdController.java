package cn.iocoder.txgy.module.training.controller.admin.analytics;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.admin.analytics.vo.PromotionRequests.Metric;
import cn.iocoder.txgy.module.training.controller.admin.analytics.vo.PromotionRequests.Profile;
import cn.iocoder.txgy.module.training.controller.admin.analytics.vo.PromotionRequests.Rule;
import cn.iocoder.txgy.module.training.service.analytics.PromotionThresholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 晋级门槛")
@RestController
@RequestMapping("/training/analytics/promotion")
@PreAuthorize("isAuthenticated()")
public class PromotionThresholdController {
    @Resource private PromotionThresholdService service;

    @GetMapping("/context") @Operation(summary = "等级路径与指标来源")
    public CommonResult<?> context() { return success(service.context()); }
    @GetMapping("/rules") @Operation(summary = "查询某条晋级路径的有效规则")
    public CommonResult<?> rules(@RequestParam String fromLevel, @RequestParam String toLevel) { return success(service.rules(fromLevel, toLevel)); }
    @GetMapping("/employees") @Operation(summary = "查询企业人员")
    public CommonResult<?> employees(@RequestParam(required = false) String keyword) { return success(service.employees(keyword)); }
    @GetMapping("/profile") @Operation(summary = "查询人员晋级档案")
    public CommonResult<?> profile(@RequestParam(required = false) Long userId) { return success(service.profile(userId)); }
    @PostMapping("/profile/save") @Operation(summary = "维护人员当前等级")
    public CommonResult<Boolean> saveProfile(@Valid @RequestBody Profile req) { service.saveProfile(req); return success(true); }
    @PostMapping("/metric/report") @Operation(summary = "课程/考试/VR/MES/HRM等子模块回传晋级指标")
    public CommonResult<Boolean> report(@Valid @RequestBody Metric req) { service.reportMetric(req); return success(true); }
    @PostMapping("/rule/update") @Operation(summary = "调整企业晋级门槛")
    public CommonResult<Boolean> updateRule(@Valid @RequestBody Rule req) { service.updateRule(req); return success(true); }
    @GetMapping("/evaluation") @Operation(summary = "实时计算晋级达标与差距")
    public CommonResult<?> evaluation(@RequestParam(required = false) Long userId) { return success(service.evaluate(userId, false)); }
    @PostMapping("/evaluate") @Operation(summary = "执行评估并保存快照")
    public CommonResult<?> evaluate(@RequestParam(required = false) Long userId) { return success(service.evaluate(userId, true)); }
}
