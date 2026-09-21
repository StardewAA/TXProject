package cn.iocoder.txgy.module.training.controller.admin.learning;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.AdminCourseTrackRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningStatRespVO;
import cn.iocoder.txgy.module.training.service.learning.AdminLearningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 学员学习中心-学习轨迹/统计")
@RestController
@RequestMapping("/training/learning/track")
@Validated
public class LearningTrackController {

    @Resource
    private AdminLearningService adminLearningService;

    @GetMapping("/course")
    @Operation(summary = "获得某学员某课程的完整学习轨迹")
    @Parameter(name = "userId", description = "学员用户 ID", required = true)
    @Parameter(name = "courseId", description = "课程 ID", required = true)
    @PreAuthorize("@ss.hasPermission('training:learning-track:query')")
    public CommonResult<AdminCourseTrackRespVO> getCourseTrack(
            @RequestParam("userId") @NotNull(message = "学员不能为空") Long userId,
            @RequestParam("courseId") @NotNull(message = "课程不能为空") Long courseId) {
        return success(adminLearningService.getCourseTrack(userId, courseId));
    }

    @GetMapping("/stats")
    @Operation(summary = "获得学习数据统计（按课程汇总）")
    @PreAuthorize("@ss.hasPermission('training:learning-track:query')")
    public CommonResult<List<LearningStatRespVO>> getLearnStats() {
        return success(adminLearningService.getLearnStats());
    }

}
