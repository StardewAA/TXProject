package cn.iocoder.txgy.module.training.controller.admin.plan;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationCheckReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationRecordPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.QualificationRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.QualificationRecordDO;
import cn.iocoder.txgy.module.training.service.plan.QualificationServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 上岗资格核查")
@RestController
@RequestMapping("/training/plan/class/qualification")
@Validated
public class PlanQualificationController {

    @Resource
    private QualificationServiceImpl qualificationService;

    @PostMapping("/check")
    @Operation(summary = "上岗资格核查（姓名 + 手机号，返回岗前/复工培训完成情况、通过岗位与是否合格）")
    @PreAuthorize("@ss.hasPermission('training:plan:qualification:check')")
    public CommonResult<QualificationRespVO> checkQualification(@Valid @RequestBody QualificationCheckReqVO reqVO) {
        return success(qualificationService.check(reqVO));
    }

    @GetMapping("/record/page")
    @Operation(summary = "上岗资格核查历史记录（分页）")
    @PreAuthorize("@ss.hasPermission('training:plan:qualification:check')")
    public CommonResult<PageResult<QualificationRecordDO>> getRecordPage(@Valid QualificationRecordPageReqVO pageReqVO) {
        return success(qualificationService.getRecordPage(pageReqVO));
    }

    @DeleteMapping("/record/delete")
    @Operation(summary = "删除上岗资格核查历史记录")
    @Parameter(name = "id", description = "记录编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:plan:qualification:check')")
    public CommonResult<Boolean> deleteRecord(@RequestParam("id") Long id) {
        qualificationService.deleteRecord(id);
        return success(true);
    }

    @PostMapping("/record/batch-delete")
    @Operation(summary = "批量删除上岗资格核查历史记录")
    @PreAuthorize("@ss.hasPermission('training:plan:qualification:check')")
    public CommonResult<Boolean> deleteRecordBatch(@RequestBody List<Long> ids) {
        qualificationService.deleteRecordBatch(ids);
        return success(true);
    }

}
