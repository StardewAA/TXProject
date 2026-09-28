package cn.iocoder.txgy.module.training.controller.admin.plan;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassProgressPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassProgressRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ProgressRegisterReqVO;
import cn.iocoder.txgy.module.training.service.plan.ClassStudentServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 班级学习进度看板")
@RestController
@RequestMapping("/training/plan/class/progress")
@Validated
public class PlanProgressController {

    @Resource
    private ClassStudentServiceImpl classStudentService;

    @GetMapping("/page")
    @Operation(summary = "班级学习进度分页（按学员聚合：学时/课程/考试/证书 + 未开始/进行中/已完成/考试未通过筛选）")
    @PreAuthorize("@ss.hasPermission('training:plan:progress')")
    public CommonResult<PageResult<ClassProgressRespVO>> getProgressPage(@Validated ClassProgressPageReqVO pageReqVO) {
        return success(classStudentService.getProgressPage(pageReqVO));
    }

    @PostMapping("/register")
    @Operation(summary = "登记学员学习进度（教务代录；正式推进由学员端学习记录/考试模块承载）")
    @PreAuthorize("@ss.hasPermission('training:plan:progress:register')")
    public CommonResult<Boolean> registerProgress(@Valid @RequestBody ProgressRegisterReqVO reqVO) {
        classStudentService.registerProgress(reqVO);
        return success(true);
    }

}
