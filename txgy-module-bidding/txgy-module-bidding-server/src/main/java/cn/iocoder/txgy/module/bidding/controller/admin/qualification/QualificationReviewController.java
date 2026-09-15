package cn.iocoder.txgy.module.bidding.controller.admin.qualification;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.ComplianceReviewRunReqVO;
import cn.iocoder.txgy.module.bidding.enums.QualificationReviewTypeEnum;
import cn.iocoder.txgy.module.bidding.service.qualification.EnterpriseQualificationComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 达标动态核查接口（15-6 只追加：要素聚合 → 快照 + PASS/RISK/FAIL）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-6；开发规范 §4.4（bidding:qualification:review）。
 */
@Tag(name = "管理后台 - 资质达标核查")
@RestController
@RequestMapping("/bidding/qualification/review")
@Validated
public class QualificationReviewController {

    @Resource
    private EnterpriseQualificationComplianceService complianceService;

    @PostMapping("/run")
    @Operation(summary = "执行一次达标核查（返回核查主键；结论快照可复算）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:review')")
    @Idempotent
    public CommonResult<Long> run(@Valid @RequestBody ComplianceReviewRunReqVO reqVO) {
        QualificationReviewTypeEnum type = reqVO.getReviewType() == null ? null
                : QualificationReviewTypeEnum.valueOf(reqVO.getReviewType());
        Long reviewer = SecurityFrameworkUtils.getLoginUserId();
        return CommonResult.success(complianceService.runComplianceReview(
                reqVO.getQualId(), type, reviewer, reqVO.getLinkedApplyId()));
    }
}
