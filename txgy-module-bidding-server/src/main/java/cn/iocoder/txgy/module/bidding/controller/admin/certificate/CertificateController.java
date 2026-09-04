package cn.iocoder.txgy.module.bidding.controller.admin.certificate;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateStatusChangeReqVO;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 证照台账（bidding certificate）。
 *
 * <p>接口编号对齐（见 docs/_bak/05-接口设计文档.md 需求映射）：
 * 建档/台账查询服务于企业资质证照使用场景（AI 自查输入、投标资格审查取数）——
 * 管理端本租户分页/详情/建档/更新/状态流转；状态流转含吊销（REVOKE 日志，等保 2.0 三级哈希链，v3.3 §13）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二/§十三＋§18；《招投标模块开发规范》§4.2
 * （Controller 只做参数校验/权限/文档）§4.4（写接口 @Idempotent）§4.5（乐观锁由服务层 CAS）。
 * 外部电子证照核验（GB/T 36906 对接）后续里程碑在 /external/ 下另立 Controller，本控制器只维护台账。
 */
@Tag(name = "管理后台 - bidding 证照台账")
@RestController
@RequestMapping("/bidding/certificate")
@Validated
public class CertificateController {

    @Resource
    private CertificateService certificateService;

    @GetMapping("/page")
    @Operation(summary = "证照台账分页（管理端）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<PageResult<CertificateRespVO>> page(@Valid CertificatePageReqVO reqVO) {
        return success(certificateService.getCertificatePage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "证照详情（管理端；含乐观锁版本供更新回传）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<CertificateRespVO> get(@RequestParam("id") Long id) {
        return success(certificateService.getCertificate(id));
    }

    @PostMapping("/create")
    @Operation(summary = "建档（证书编号租户内唯一；幂等）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:create')")
    @Idempotent
    public CommonResult<Long> create(@Validated(CertificateSaveReqVO.Create.class)
                                     @RequestBody CertificateSaveReqVO reqVO) {
        return success(certificateService.createCertificate(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新证照字段（乐观锁 CAS；变更差异留痕）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:update')")
    @Idempotent
    public CommonResult<Boolean> update(@Validated(CertificateSaveReqVO.Update.class)
                                        @RequestBody CertificateSaveReqVO reqVO) {
        certificateService.updateCertificate(reqVO);
        return success(true);
    }

    @PostMapping("/change-status")
    @Operation(summary = "台账状态流转（吊销/注销记 REVOKE，其余 STATUS_CHANGE；乐观锁 CAS）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:update')")
    @Idempotent
    public CommonResult<Boolean> changeStatus(@Valid @RequestBody CertificateStatusChangeReqVO reqVO) {
        // 操作来源/IP 当前取默认（MANUAL/空），对接登录上下文后由安全框架注入（后续里程碑）
        certificateService.changeStatus(reqVO.getCertificateId(), reqVO.getTargetStatus(), reqVO.getVersion(),
                reqVO.getReason(), CertificateChangeSourceEnum.MANUAL, null);
        return success(true);
    }
}
