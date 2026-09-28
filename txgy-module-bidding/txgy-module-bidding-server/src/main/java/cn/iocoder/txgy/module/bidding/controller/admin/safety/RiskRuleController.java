package cn.iocoder.txgy.module.bidding.controller.admin.safety;

// [规范依据]契约《后端变更请求-电子证照域e2e暴露问题-2026-09-21》P1 方案 A：
//   前端已按 /bidding/risk-rule/* 接线（api/bidding/safetyRisk 规则配置区，静默空态降级），后端全无实现。
// [业务目标] 风险规则实体 CRUD：分页/新增/修改/启停/删除（权限 bidding:safety-risk:rule，system_menu 已注册）。
// [为什么] 规则实体独立于 rule-config（后者是阈值键值）：规则=名称/触发条件/影响范围/启用禁用，
//   ruleKey 可选挂钩阈值键，避免把"规则"塞进键值配置里导致无法启停与留痕。

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskRulePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskRuleRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyRiskRuleSaveReqVO;
import cn.iocoder.txgy.module.bidding.service.safety.SafetyRiskRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台 - 安许风险规则（契约 e2e P1 方案A）")
@RestController
@RequestMapping("/bidding/risk-rule")
@Validated
public class RiskRuleController {

    @Resource
    private SafetyRiskRuleService safetyRiskRuleService;

    @GetMapping("/page")
    @Operation(summary = "风险规则分页（规则名称/启用/命中等级过滤）")
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:rule')")
    public CommonResult<PageResult<SafetyRiskRuleRespVO>> page(@Valid SafetyRiskRulePageReqVO reqVO) {
        return CommonResult.success(safetyRiskRuleService.page(reqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "新增风险规则（enabled 缺省=启用）")
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:rule')")
    @Idempotent
    public CommonResult<Long> create(@Valid @RequestBody SafetyRiskRuleSaveReqVO reqVO) {
        return CommonResult.success(safetyRiskRuleService.create(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改风险规则（id + version 必传，乐观锁 CAS）")
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:rule')")
    @Idempotent
    public CommonResult<Boolean> update(@Valid @RequestBody SafetyRiskRuleSaveReqVO reqVO) {
        safetyRiskRuleService.update(reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/status")
    @Operation(summary = "启用/禁用规则")
    @Parameter(name = "id", description = "规则主键", required = true)
    @Parameter(name = "enabled", description = "true=启用 / false=禁用", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:rule')")
    @Idempotent
    public CommonResult<Boolean> updateStatus(@RequestParam("id") Long id,
            @RequestParam("enabled") Boolean enabled) {
        safetyRiskRuleService.updateStatus(id, enabled);
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除风险规则（逻辑删除）")
    @Parameter(name = "id", description = "规则主键", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:safety-risk:rule')")
    @Idempotent
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        safetyRiskRuleService.delete(id);
        return CommonResult.success(true);
    }
}
