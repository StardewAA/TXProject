package cn.iocoder.txgy.module.training.controller.admin.plan;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationCheckReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationRespVO;
import cn.iocoder.txgy.module.training.service.plan.QualificationServiceImpl;
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

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 上岗资格核查")
@RestController
@RequestMapping("/training/plan/class/qualification")
@Validated
public class PlanQualificationController {

    @Resource
    private QualificationServiceImpl qualificationService;

    @PostMapping("/check")
    @Operation(summary = "上岗资格核查（传入人员 ID + 项目 ID，返回岗前/复工培训完成情况与是否合格）")
    @PreAuthorize("@ss.hasPermission('training:plan:qualification:check')")
    public CommonResult<QualificationRespVO> checkQualification(@Valid @RequestBody QualificationCheckReqVO reqVO) {
        return success(qualificationService.check(reqVO));
    }

}
