package cn.iocoder.txgy.module.bidding.controller.admin.certificate;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateVerifyReqVO;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateVerifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyBatchReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyBatchRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyHistoryPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertVerifyHistoryRespVO;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 证照验真管理接口（GB/T 36906 verifyinfo 客户端编排）。
 *
 * <p>规范出处：GB/T 36906—2018 §6.4；《电子证件全流程管理_数据库设计.md》v3.3 §17.5；
 * 《招投标模块开发规范》§4.4（Controller 权限码 bidding:certificate:verify，菜单脚本见 sql/history/2026-09-08-…）。
 */
@Tag(name = "管理后台 - 证照验真")
@RestController
@RequestMapping("/bidding/certificate-verify")
@Validated
public class CertificateVerifyController {

    @Resource
    private CertificateVerifyService certificateVerifyService;

    @GetMapping("/history/page")
    @Operation(summary = "验真历史分页（管理端；追溯读出口，L3 报文原文不下发）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<PageResult<CertVerifyHistoryRespVO>> historyPage(@Valid CertVerifyHistoryPageReqVO reqVO) {
        return CommonResult.success(certificateVerifyService.getVerifyHistoryPage(reqVO));
    }

    @PostMapping("/verify")
    @Idempotent
    @Operation(summary = "发起验真（verifyinfo；同分钟幂等，返回验真历史主键）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:verify')")
    public CommonResult<Long> verify(@Valid @RequestBody CertificateVerifyReqVO reqVO) {
        String channel = reqVO.getVerifyChannel() == null ? "FOUR_PLATFORM" : reqVO.getVerifyChannel();
        return CommonResult.success(certificateVerifyService.verifyCertificate(
                reqVO.getCertId(), channel, getLoginUserId()));
    }

    @PostMapping("/verify-file")
    @Idempotent
    @Operation(summary = "文件验真（GB/T 36906 verifyfile；取当前生效文件指纹，同分钟幂等）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:verify')")
    public CommonResult<Long> verifyFile(@Valid @RequestBody CertificateVerifyReqVO reqVO) {
        String channel = reqVO.getVerifyChannel() == null ? "FOUR_PLATFORM" : reqVO.getVerifyChannel();
        return CommonResult.success(certificateVerifyService.verifyCertificateFile(
                reqVO.getCertId(), channel, getLoginUserId()));
    }

    @PostMapping("/verify-batch")
    @Idempotent
    @Operation(summary = "批量验真（Q2：certIds[] 逐条验真，行级回执，单个失败不影响其余）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:verify')")
    public CommonResult<CertVerifyBatchRespVO> verifyBatch(@Valid @RequestBody CertVerifyBatchReqVO reqVO) {
        return CommonResult.success(certificateVerifyService.verifyCertificateBatch(
                reqVO.getCertIds(), reqVO.getVerifyChannel(), getLoginUserId()));
    }

    /** 登录用户：平台 SecurityUtils 兼容（演示走 ss.hasPermission 鉴权）。 */
    private Long getLoginUserId() {
        return cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
    }
}