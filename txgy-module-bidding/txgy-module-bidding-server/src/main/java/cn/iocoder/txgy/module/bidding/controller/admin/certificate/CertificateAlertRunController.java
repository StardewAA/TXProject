package cn.iocoder.txgy.module.bidding.controller.admin.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateAlertPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateAlertRespVO;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 到期预警运行接口（演示闭环：手动触发一次预警扫描/发件箱派发）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九（扫描口径）；
 * 《招投标模块开发规范》§4.4（权限码 bidding:certificate:alert-run）。
 * <p>设计遵循/针对：**定时化已由 {@code job.CredentialAlertJob}（@Scheduled，开关默认关）承担**；
 * 本端点保留给运维补跑、联调与单次重扫（扫描同日幂等、重跑安全）。
 * 修订说明：此前写"由部署方调度中心按 cron 调用本端点"，但全仓无 infra_job/JobHandler/XXL-Job 实现，
 * 即线上并无调度会调它——已按事实改为本模块 Job，避免"文档说有、实际没有"的静默失效；企业资质 valid_to 的 QUAL_EXPIRY 扩展扫描在 M-later 按 §十五联动口径并入。
 */
@Tag(name = "管理后台 - 到期预警运行")
@RestController
@RequestMapping("/bidding/certificate-alert-run")
@Validated
public class CertificateAlertRunController {

    @Resource
    private CertificateAlertService certificateAlertService;

    @GetMapping("/alert/page")
    @Operation(summary = "证照到期预警清单分页（决策③：补齐'只扫不读'的读出口；含 version 供处理时 CAS）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<PageResult<CertificateAlertRespVO>> getAlertPage(@Valid CertificateAlertPageReqVO reqVO) {
        return CommonResult.success(certificateAlertService.getAlertPage(reqVO));
    }

    @PostMapping("/scan")
    @Idempotent
    @Operation(summary = "手动触发预警扫描（生成 alert_records/派发 outbox；以今日为准）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:alert-run')")
    public CommonResult<Integer> scan() {
        return CommonResult.success(certificateAlertService.scanExpiryAlerts(LocalDate.now()));
    }

    @PostMapping("/dispatch")
    @Idempotent
    @Operation(summary = "手动派发发件箱（PENDING/FAILED 到点出箱，退避重试/DEAD 死信）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:alert-run')")
    public CommonResult<Integer> dispatch() {
        return CommonResult.success(certificateAlertService.dispatchPending(100, LocalDateTime.now()));
    }
}