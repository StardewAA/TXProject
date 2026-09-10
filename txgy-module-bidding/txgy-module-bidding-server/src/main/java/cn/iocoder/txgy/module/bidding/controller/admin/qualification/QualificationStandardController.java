package cn.iocoder.txgy.module.bidding.controller.admin.qualification;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.StandardCreateReqVO;
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
 * 资质标准库管理接口（15-2：标准登记/升版）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-2；开发规范 §4.4（bidding:qualification:standard）。
 */
@Tag(name = "管理后台 - 资质标准库")
@RestController
@RequestMapping("/bidding/qualification/standard")
@Validated
public class QualificationStandardController {

    @Resource
    private EnterpriseQualificationComplianceService complianceService;

    @PostMapping("/create")
    @Operation(summary = "标准登记（同版本判重，可升版）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:standard')")
    @Idempotent
    public CommonResult<Long> create(@Valid @RequestBody StandardCreateReqVO reqVO) {
        return CommonResult.success(complianceService.createStandard(
                reqVO.getQualSerial(), reqVO.getQualCategory(), reqVO.getQualGrade(), reqVO.getStdVersion(),
                reqVO.getReqStaff(), reqVO.getReqAssets(), reqVO.getReqPerformance(), reqVO.getReqEquipment(),
                reqVO.getEffectiveFrom(), reqVO.getEffectiveTo(), reqVO.getSourceRef()));
    }
}
