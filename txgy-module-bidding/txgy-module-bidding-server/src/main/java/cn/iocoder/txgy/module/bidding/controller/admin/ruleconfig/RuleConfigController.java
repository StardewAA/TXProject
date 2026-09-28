package cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.RuleConfigItemRespVO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigAuditPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigAuditRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigHistoryRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo.RuleConfigSaveReqVO;
import cn.iocoder.txgy.module.bidding.service.ruleconfig.RuleConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 规则配置管理接口（保存/发布/回滚/历史）——运维与平台管理员使用。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1(d)（save-draft / publish / gray / rollback / history）；
 * 《招投标模块开发规范》§4.4（RESTful + 权限注解）、§4.5（写接口幂等 + 乐观锁）。
 * <p>为什么读接口与写接口分权：读（/bidding/meta/configs）是业务页面刚需，登录即可；
 * 写会改变**全租户**的判定行为，必须独立高权限，且每次写都强制"变更原因"并落哈希链审计。
 * <p>业务目标：配置改动像发版一样可控可回滚，杜绝"手改数据库"带来的不可追溯变更。
 */
@Tag(name = "管理后台 - 规则配置管理")
@RestController
@RequestMapping("/bidding/rule-config")
@Validated
public class RuleConfigController {

    @Resource
    private RuleConfigService ruleConfigService;

    @GetMapping("/get")
    @Operation(summary = "获得单个规则配置（含来源 TENANT/PLATFORM/FALLBACK 与版本）")
    @Parameter(name = "ruleKey", description = "规则键", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:rule-config:query')")
    public CommonResult<RuleConfigItemRespVO> get(@RequestParam("ruleKey") String ruleKey) {
        return CommonResult.success(ruleConfigService.getItem(ruleKey));
    }

    @PostMapping("/save")
    @Operation(summary = "保存或发布规则配置（类型/边界/一致性三道校验 + 审计留痕）")
    @PreAuthorize("@ss.hasPermission('bidding:rule-config:update')")
    @Idempotent
    public CommonResult<Long> save(@Valid @RequestBody RuleConfigSaveReqVO reqVO) {
        return CommonResult.success(ruleConfigService.saveOrPublish(reqVO));
    }

    @PostMapping("/rollback")
    @Operation(summary = "回滚到指定历史版本（以新版本复制旧值实现，历史不可改写）")
    @PreAuthorize("@ss.hasPermission('bidding:rule-config:update')")
    @Idempotent
    public CommonResult<Long> rollback(@RequestParam("ruleKey") String ruleKey,
                                       @RequestParam("targetVersion") Integer targetVersion,
                                       @RequestParam("changeReason") String changeReason) {
        return CommonResult.success(ruleConfigService.rollback(ruleKey, targetVersion, changeReason));
    }

    @GetMapping("/audit/page")
    @Operation(summary = "规则读取审计分页（谁在什么场景读了哪一版、命中哪一层；争议追溯用）")
    @PreAuthorize("@ss.hasPermission('bidding:rule-config:query')")
    public CommonResult<PageResult<RuleConfigAuditRespVO>> auditPage(@Valid RuleConfigAuditPageReqVO reqVO) {
        return CommonResult.success(ruleConfigService.getAuditPage(reqVO));
    }

    @GetMapping("/history")
    @Operation(summary = "规则变更历史（含哈希链，供审计比对）")
    @Parameter(name = "ruleKey", description = "规则键", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:rule-config:query')")
    public CommonResult<List<RuleConfigHistoryRespVO>> history(@RequestParam("ruleKey") String ruleKey) {
        return CommonResult.success(ruleConfigService.history(ruleKey));
    }
}
