package cn.iocoder.txgy.module.bidding.controller.admin.certificate;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecycleApplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecycleAuditReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecycleEffectReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecyclePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertLifecycleRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.CertLifecycleRecordDO;
import cn.iocoder.txgy.module.bidding.service.certificate.CertLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
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
 * 证件/资质全生命周期接口（变更 / 续期 / 注销吊销 / 年检，含生效回写主档）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 3/5/6/7（四类流程）；
 * 用户范围口径（2026-09-13 确认）：做到"证照全生命周期管理"——变更、注销、年检、到期提醒、版本管理，
 * **不做签发**（签发流程不在此接口集内）；《招投标模块开发规范》§4.2/§4.4/§4.5/§4.7。
 * <p>为什么这样写：审核通过与"已生效"是两个不同时点（续期要拿到新证、注销要等决定书下达），
 * 因此 audit 与 effect 分成两个接口，两个时点都能追溯；变更按字段提交、服务层自动归组，前端只需一次提交。
 * <p>业务目标：四类生命周期操作统一走"申请 → 审核 → 生效"，生效自动同步台账并留痕，
 * 替代"直接改库、事后说不清"的台账模式。
 */
@Tag(name = "管理后台 - 证件全生命周期")
@RestController
@RequestMapping("/bidding/cert-lifecycle")
@Validated
public class CertLifecycleController {

    @Resource
    private CertLifecycleService certLifecycleService;

    @PostMapping("/apply")
    @Operation(summary = "发起生命周期流程（变更/续期/注销吊销/年检）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:lifecycle:apply')")
    @Idempotent
    public CommonResult<Long> apply(@Valid @RequestBody CertLifecycleApplyReqVO reqVO) {
        return CommonResult.success(certLifecycleService.apply(reqVO));
    }

    @PostMapping("/audit")
    @Operation(summary = "审核流转（受理/审核中 → 通过 / 驳回）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:lifecycle:audit')")
    @Idempotent
    public CommonResult<Boolean> audit(@Valid @RequestBody CertLifecycleAuditReqVO reqVO) {
        certLifecycleService.audit(reqVO.getId(), reqVO.getTargetStatus(), reqVO.getVersion(), reqVO.getRemark());
        return CommonResult.success(true);
    }

    @PostMapping("/effect")
    @Operation(summary = "生效（回写主档：续期有效期/变更字段/注销状态/下次年检日）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:lifecycle:audit')")
    @Idempotent
    public CommonResult<Boolean> effect(@Valid @RequestBody CertLifecycleEffectReqVO reqVO) {
        certLifecycleService.effect(reqVO.getId(), reqVO.getVersion(), reqVO.getRemark());
        return CommonResult.success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得生命周期记录")
    @Parameter(name = "id", description = "记录主键", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:certificate:lifecycle:query')")
    public CommonResult<CertLifecycleRespVO> getRecord(@RequestParam("id") Long id) {
        return CommonResult.success(toVO(certLifecycleService.getRecord(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询生命周期记录（变更/年检台账）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:lifecycle:query')")
    public CommonResult<PageResult<CertLifecycleRespVO>> pageRecord(@Valid CertLifecyclePageReqVO reqVO) {
        PageResult<CertLifecycleRecordDO> page = certLifecycleService.pageRecord(reqVO);
        return CommonResult.success(new PageResult<>(
                page.getList().stream().map(this::toVO).toList(), page.getTotal()));
    }

    @GetMapping("/list-by-biz")
    @Operation(summary = "获得某证件的完整生命周期轨迹")
    @Parameter(name = "bizDomain", description = "业务对象域 code", required = true, example = "CERT")
    @Parameter(name = "bizId", description = "业务对象主键", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:certificate:lifecycle:query')")
    public CommonResult<List<CertLifecycleRespVO>> listByBiz(@RequestParam("bizDomain") String bizDomain,
                                                             @RequestParam("bizId") Long bizId) {
        return CommonResult.success(certLifecycleService.listByBiz(bizDomain, bizId).stream()
                .map(this::toVO).toList());
    }

    private CertLifecycleRespVO toVO(CertLifecycleRecordDO record) {
        CertLifecycleRespVO vo = new CertLifecycleRespVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }
}
