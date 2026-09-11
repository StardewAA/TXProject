package cn.iocoder.txgy.module.bidding.controller.admin.qualification;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApproveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationRejectReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationRenewReqVO;
import cn.iocoder.txgy.module.bidding.service.qualification.EnterpriseQualificationService;
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

/**
 * 企业资质主档/申报流管理接口（建档 + 延续申报 RENEW 全流程）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十五 表 15-1/15-7（建档/延续申报/办结回写）；
 * 《招投标模块开发规范》§4.4（权限码 bidding:qualification:*，菜单脚本见 sql/history/2026-09-08-…）。
 */
@Tag(name = "管理后台 - 企业资质")
@RestController
@RequestMapping("/bidding/qualification")
@Validated
public class QualificationController {

    @Resource
    private EnterpriseQualificationService enterpriseQualificationService;

    @PostMapping("/create")
    @Idempotent
    @Operation(summary = "建档（UK 判重；binded_cert_id 联动台账）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:create')")
    public CommonResult<Long> create(@Valid @RequestBody EnterpriseQualificationSaveReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.createQualification(reqVO));
    }

    @PostMapping("/renew")
    @Idempotent
    @Operation(summary = "延续申报（提交 SUBMITTED；主档置 PENDING_RENEW）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:renew')")
    public CommonResult<Long> renew(@Valid @RequestBody QualificationRenewReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.submitRenewalApply(
                reqVO.getQualificationId(), reqVO.getVersion(), getLoginUserId(),
                reqVO.getDueDate(), reqVO.getRemark()));
    }

    @PostMapping("/approve")
    @Idempotent
    @Operation(summary = "延续审批通过（APPROVED→ISSUED；回写主档 VALID+new_valid_to）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:approve')")
    public CommonResult<Long> approve(@Valid @RequestBody QualificationApproveReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.approveRenewalApply(
                reqVO.getApplyId(), reqVO.getVersion(), reqVO.getResultNo(), reqVO.getNewCertNo(),
                reqVO.getNewValidTo(), getLoginUserId()));
    }

    @PostMapping("/reject")
    @Idempotent
    @Operation(summary = "延续审批退回（REJECTED；主档还原 VALID）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:reject')")
    public CommonResult<Long> reject(@Valid @RequestBody QualificationRejectReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.rejectRenewalApply(
                reqVO.getApplyId(), reqVO.getVersion(), getLoginUserId(), reqVO.getRemark()));
    }

    /** 登录用户：平台 SecurityFrameworkUtils 兼容。 */
    private Long getLoginUserId() {
        return cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
    }
}