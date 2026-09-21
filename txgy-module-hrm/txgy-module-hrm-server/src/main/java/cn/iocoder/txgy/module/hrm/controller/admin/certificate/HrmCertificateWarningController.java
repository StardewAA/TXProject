package cn.iocoder.txgy.module.hrm.controller.admin.certificate;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningHandleReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningRespVO;
import cn.iocoder.txgy.module.hrm.service.certificate.HrmCertificateWarningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 证件预警")
@RestController
@RequestMapping("/hrm/certificate-warning")
@Validated
public class HrmCertificateWarningController {

    @Resource
    private HrmCertificateWarningService certificateWarningService;

    @GetMapping("/page")
    @Operation(summary = "获得证件预警分页")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:warning:query')")
    public CommonResult<PageResult<HrmCertificateWarningRespVO>> getCertificateWarningPage(
            @Validated HrmCertificateWarningPageReqVO pageReqVO) {
        return success(certificateWarningService.getCertificateWarningPage(pageReqVO));
    }

    @PutMapping("/handle")
    @Operation(summary = "处理证件预警",
            description = "标记已处理、已忽略，或将已处理/已忽略重新启用为待处理")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:warning:handle')")
    public CommonResult<Boolean> handleCertificateWarning(@Valid @RequestBody HrmCertificateWarningHandleReqVO reqVO) {
        certificateWarningService.handleCertificateWarning(reqVO);
        return success(true);
    }

    @GetMapping("/pending-count")
    @Operation(summary = "获得待处理证件预警数量")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:warning:query')")
    public CommonResult<Long> getPendingWarningCount() {
        return success(certificateWarningService.getPendingWarningCount());
    }

}
