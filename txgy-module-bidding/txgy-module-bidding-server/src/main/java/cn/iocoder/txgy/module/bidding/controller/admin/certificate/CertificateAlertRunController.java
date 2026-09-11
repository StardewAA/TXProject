package cn.iocoder.txgy.module.bidding.controller.admin.certificate;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
 * <p>设计遵循/针对：任务定时化由部署方调度中心按 cron 调用本端点（扫描幂等键/退避出箱均在服务内，
 * 重复触发安全）；企业资质 valid_to 的 QUAL_EXPIRY 扩展扫描在 M-later 按 §十五联动口径并入。
 */
@Tag(name = "管理后台 - 到期预警运行")
@RestController
@RequestMapping("/bidding/certificate-alert-run")
@Validated
public class CertificateAlertRunController {

    @Resource
    private CertificateAlertService certificateAlertService;

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