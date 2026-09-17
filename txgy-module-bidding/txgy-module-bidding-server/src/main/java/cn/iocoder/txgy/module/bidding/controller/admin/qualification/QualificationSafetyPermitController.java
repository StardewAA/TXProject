package cn.iocoder.txgy.module.bidding.controller.admin.qualification;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.PermitCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.SafetyPermitStatusChangeReqVO;
import cn.iocoder.txgy.module.bidding.service.qualification.QualificationSafetyPermitService;
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
 * 安全生产许可证管理接口（15-8：建档 + 状态流转）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-8；《招投标模块开发规范》§4.4
 * （权限码 bidding:qualification:permit，菜单脚本 sql/history/2026-09-11-…）。
 */
@Tag(name = "管理后台 - 安许证")
@RestController
@RequestMapping("/bidding/qualification/permit")
@Validated
public class QualificationSafetyPermitController {

    @Resource
    private QualificationSafetyPermitService qualificationSafetyPermitService;

    @PostMapping("/create")
    @Operation(summary = "建档（permit_no UK 判重）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:permit')")
    @Idempotent
    public CommonResult<Long> create(@Valid @RequestBody PermitCreateReqVO reqVO) {
        return CommonResult.success(qualificationSafetyPermitService.createPermit(
                reqVO.getEntId(), reqVO.getPermitNo(), reqVO.getIssuingAuthority(), reqVO.getIssueDate(),
                reqVO.getValidTo(), reqVO.getLegalPerson(), reqVO.getBindedCertId(), reqVO.getRemark()));
    }

    @PostMapping("/status")
    @Operation(summary = "状态流转（迁移表 + version CAS）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:permit')")
    @Idempotent
    public CommonResult<Boolean> changeStatus(@Valid @RequestBody SafetyPermitStatusChangeReqVO reqVO) {
        qualificationSafetyPermitService.changeStatus(reqVO.getPermitId(), reqVO.getTargetStatus(),
                reqVO.getVersion(), reqVO.getReason());
        return CommonResult.success(true);
    }
}
