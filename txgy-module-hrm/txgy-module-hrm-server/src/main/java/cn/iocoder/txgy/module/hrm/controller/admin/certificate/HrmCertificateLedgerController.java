package cn.iocoder.txgy.module.hrm.controller.admin.certificate;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.ledger.HrmCertificateLedgerStatusReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.renewal.HrmCertificateRenewalCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.renewal.HrmCertificateRenewalRespVO;
import cn.iocoder.txgy.module.hrm.service.certificate.HrmCertificateLedgerService;
import cn.iocoder.txgy.module.hrm.service.certificate.HrmCertificateRenewalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 证件台账")
@RestController
@RequestMapping("/hrm/certificate")
@Validated
public class HrmCertificateLedgerController {

    @Resource
    private HrmCertificateLedgerService certificateLedgerService;
    @Resource
    private HrmCertificateRenewalService certificateRenewalService;

    @PostMapping("/create")
    @Operation(summary = "创建证件台账记录")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:create')")
    public CommonResult<Long> createCertificateLedger(@Valid @RequestBody HrmCertificateLedgerSaveReqVO reqVO) {
        return success(certificateLedgerService.createCertificateLedger(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新证件台账记录")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:update')")
    public CommonResult<Boolean> updateCertificateLedger(@Valid @RequestBody HrmCertificateLedgerSaveReqVO reqVO) {
        certificateLedgerService.updateCertificateLedger(reqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "更新证件状态", description = "仅支持手工注销，或将注销的证件恢复为自动计算状态")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:update')")
    public CommonResult<Boolean> updateCertificateLedgerStatus(
            @Valid @RequestBody HrmCertificateLedgerStatusReqVO reqVO) {
        certificateLedgerService.updateCertificateLedgerStatus(reqVO.getId(), reqVO.getStatus());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除证件台账记录")
    @Parameter(name = "id", description = "证件台账编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:delete')")
    public CommonResult<Boolean> deleteCertificateLedger(@RequestParam("id") Long id) {
        certificateLedgerService.deleteCertificateLedger(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得证件台账记录")
    @Parameter(name = "id", description = "证件台账编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:query')")
    public CommonResult<HrmCertificateLedgerRespVO> getCertificateLedger(@RequestParam("id") Long id) {
        return success(certificateLedgerService.getCertificateLedgerRespVO(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得证件台账分页")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:query')")
    public CommonResult<PageResult<HrmCertificateLedgerRespVO>> getCertificateLedgerPage(
            @Validated HrmCertificateLedgerPageReqVO pageReqVO) {
        return success(certificateLedgerService.getCertificateLedgerPage(pageReqVO));
    }

    @PostMapping("/{id}/renewals")
    @Operation(summary = "办理证件续证/复审", description = "只追加历史记录，不覆盖原证件")
    @Parameter(name = "id", description = "证件台账编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:renewal')")
    public CommonResult<Long> createCertificateRenewal(@PathVariable("id") Long id,
                                                       @Valid @RequestBody HrmCertificateRenewalCreateReqVO reqVO) {
        reqVO.setCertificateId(id);
        return success(certificateRenewalService.createCertificateRenewal(reqVO));
    }

    @GetMapping("/renewal/list")
    @Operation(summary = "获得证件的续证/复审历史")
    @Parameter(name = "certificateId", description = "证件台账编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:query')")
    public CommonResult<List<HrmCertificateRenewalRespVO>> getCertificateRenewalList(
            @RequestParam("certificateId") Long certificateId) {
        return success(certificateRenewalService.getCertificateRenewalList(certificateId));
    }

}
