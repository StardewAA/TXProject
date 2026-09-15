package cn.iocoder.txgy.module.bidding.controller.admin.safety;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.KeyPostChangeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.MultiPostCheckReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.MultiPostCheckRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.PersonRiskRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.RiskSnapshotRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyGraceRegisterReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.safety.vo.SafetyPermitRiskRespVO;
import cn.iocoder.txgy.module.bidding.service.safety.SafetyPermitRiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 安许 A/B/C 联动风险接口（招投标 / 施工许可 / 现场监督检查**共用**的风险状态入口）。
 *
 * <p>规范出处：用户业务口径（"联动场景：招投标、施工许可、现场监督检查读取同一风险状态" +
 * "新增对同一项目关键岗位人员频繁变更的监控" + "兼任两个及以上岗位的须持另一岗位证书"）；
 * 《电子资质七大模块技术方案.md》§1.3（7 个接口）、§1.4（拦截点）。
 * <p>为什么读接口单列一个 Controller：风险状态被三个业务线读取，若各自实现取数逻辑，
 * 人员流动期间会出现"招投标拦住、现场检查放行"的矛盾结论；统一入口保证同一时刻只有一份判定。
 * <p>业务目标：不达标可发现、可阻断、可追溯，且三方口径一致。
 */
@Tag(name = "管理后台 - 安许风险联动（A/B/C 三类人员）")
@RestController
@RequestMapping("/bidding/safety-permit")
@Validated
public class SafetyPermitRiskController {

    @Resource
    private SafetyPermitRiskService safetyPermitRiskService;

    @GetMapping("/risk")
    @Operation(summary = "读取安许风险状态（招投标/施工许可/现场监督统一口径；null=未评估不阻断）")
    @Parameter(name = "entId", description = "企业主体", required = true)
    @Parameter(name = "asOf", description = "基准日（空=今天，可回填做历史预演）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:permit:query')")
    public CommonResult<SafetyPermitRiskRespVO> getRisk(@RequestParam("entId") Long entId,
            @RequestParam(value = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return CommonResult.success(safetyPermitRiskService.getRisk(entId, asOf));
    }

    @PostMapping("/risk/recheck")
    @Operation(summary = "重算安许风险（dryRun=true 只算不落库，用于核对与排查）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:permit:update')")
    @Idempotent
    public CommonResult<SafetyPermitRiskRespVO> recheck(@RequestParam("permitId") Long permitId,
            @RequestParam(value = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return CommonResult.success(safetyPermitRiskService.evaluate(permitId, asOf, dryRun));
    }

    @GetMapping("/risk/history")
    @Operation(summary = "安许风险快照分页（含哈希链，审计可比对）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:permit:query')")
    public CommonResult<PageResult<RiskSnapshotRespVO>> history(
            @RequestParam("permitId") Long permitId,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return CommonResult.success(safetyPermitRiskService.pageSnapshot(permitId, pageNo, pageSize));
    }

    @PostMapping("/grace")
    @Operation(summary = "登记整改宽限（原因须在白名单内，天数不得超配置上限）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:permit:update')")
    @Idempotent
    public CommonResult<Boolean> registerGrace(@Valid @RequestBody SafetyGraceRegisterReqVO reqVO) {
        safetyPermitRiskService.registerGrace(reqVO);
        return CommonResult.success(true);
    }

    @PostMapping("/personnel/multi-post-check")
    @Operation(summary = "多岗兼任合规校验（兼任岗位须持对应考核合格证；结论落库留痕）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:query')")
    @Idempotent
    public CommonResult<MultiPostCheckRespVO> multiPostCheck(@Valid @RequestBody MultiPostCheckReqVO reqVO) {
        return CommonResult.success(safetyPermitRiskService.checkMultiPost(reqVO));
    }

    @GetMapping("/personnel/risk")
    @Operation(summary = "读取人员级风险（12 个月内更换受聘企业次数达阈值即异常）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:profile:query')")
    public CommonResult<PersonRiskRespVO> personRisk(@RequestParam("personId") Long personId) {
        return CommonResult.success(safetyPermitRiskService.getPersonRisk(personId));
    }

    @GetMapping("/project/key-post-changes")
    @Operation(summary = "项目关键岗位变更统计（防挂靠/管理失控，超阈值高亮）")
    @PreAuthorize("@ss.hasPermission('bidding:project:query')")
    public CommonResult<KeyPostChangeRespVO> keyPostChanges(@RequestParam("projectId") Long projectId,
            @RequestParam(value = "windowDays", required = false) Integer windowDays) {
        return CommonResult.success(safetyPermitRiskService.getKeyPostChanges(projectId, windowDays));
    }
}
