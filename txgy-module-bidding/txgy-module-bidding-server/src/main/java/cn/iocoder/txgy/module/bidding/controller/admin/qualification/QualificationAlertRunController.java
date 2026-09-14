package cn.iocoder.txgy.module.bidding.controller.admin.qualification;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.service.qualification.QualificationAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 资质类到期预警运行接口（QUAL_EXPIRY 三源扫描演示端点）。
 *
 * <p>规范出处：v3.3 §十五-到期预警复用（企业资质/安许/人员注册 valid 到期 → cert_alert 域）；
 * 开发规范 §4.4（bidding:qualification:alert-run）。定时化由调度中心 cron 调用（扫描幂等，重跑安全）。
 */
@Tag(name = "管理后台 - 资质到期预警运行")
@RestController
@RequestMapping("/bidding/qualification-alert-run")
@Validated
public class QualificationAlertRunController {

    @Resource
    private QualificationAlertService qualificationAlertService;

    @PostMapping("/scan")
    @Operation(summary = "手动触发 QUAL_EXPIRY 三源扫描（同日幂等）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:alert-run')")
    @Idempotent
    public CommonResult<Integer> scan() {
        return CommonResult.success(qualificationAlertService.scanQualificationExpiries(LocalDate.now()));
    }
}
