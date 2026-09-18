package cn.iocoder.txgy.module.training.controller.admin.learning;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.AssignCandidateRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearnerProgressRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearnerTaskRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningAssignReqVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningClassStudentRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningTaskPageReqVO;
import cn.iocoder.txgy.module.training.service.learning.AdminLearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 学员学习中心-学习任务")
@RestController
@RequestMapping("/training/learning/task")
@Validated
public class LearningTaskController {

    @Resource
    private AdminLearningService adminLearningService;

    @GetMapping("/page")
    @Operation(summary = "获得学习任务分页")
    @PreAuthorize("@ss.hasPermission('training:learner-task:query')")
    public CommonResult<PageResult<LearnerTaskRespVO>> getTaskPage(@Valid LearningTaskPageReqVO pageReqVO) {
        return success(adminLearningService.getTaskPage(pageReqVO));
    }

    @PostMapping("/assign")
    @Operation(summary = "课程指派给学员")
    @PreAuthorize("@ss.hasPermission('training:learner-task:assign')")
    public CommonResult<Integer> assign(@Valid @RequestBody LearningAssignReqVO reqVO) {
        return success(adminLearningService.assign(reqVO));
    }

    @GetMapping("/assign-candidates")
    @Operation(summary = "获得指派候选（课程 + 已有学员）")
    @PreAuthorize("@ss.hasPermission('training:learner-task:assign')")
    public CommonResult<AssignCandidateRespVO> getAssignCandidates() {
        return success(adminLearningService.getAssignCandidates());
    }

    @GetMapping("/class-students")
    @Operation(summary = "获得培训班在班学员（学习中心「筛选学员 / 课程指派」候选，与培训管理同步）")
    @PreAuthorize("@ss.hasAnyPermissions('training:learner-task:query', 'training:learner-task:assign')")
    public CommonResult<List<LearningClassStudentRespVO>> listClassStudents() {
        return success(adminLearningService.listClassStudents());
    }

    @GetMapping("/student-progress")
    @Operation(summary = "获得学员整体学习进度（与培训管理班级学习进度打通，按学员聚合）")
    @Parameter(name = "classId", description = "培训班 ID（可选，为空时统计全部在班学员）", example = "1")
    @PreAuthorize("@ss.hasAnyPermissions('training:learner-task:query', 'training:learning-track:query')")
    public CommonResult<List<LearnerProgressRespVO>> getStudentProgress(
            @RequestParam(value = "classId", required = false) Long classId) {
        return success(adminLearningService.getLearnerProgress(classId));
    }

}
