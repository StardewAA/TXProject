package cn.iocoder.txgy.module.training.controller.admin.course;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CoursePageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseRespVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.service.course.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 培训课程管理")
@RestController
@RequestMapping("/training/course")
@Validated
public class CourseController {

    @Resource
    private CourseService courseService;

    @PostMapping("/create")
    @Operation(summary = "创建课程（初始状态：草稿）")
    @PreAuthorize("@ss.hasPermission('training:course:create')")
    public CommonResult<Long> createCourse(@Valid @RequestBody CourseSaveReqVO createReqVO) {
        return success(courseService.createCourse(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改课程（仅草稿状态可编辑）")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Boolean> updateCourse(@Valid @RequestBody CourseSaveReqVO updateReqVO) {
        courseService.updateCourse(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除课程（仅草稿状态可删除）")
    @Parameter(name = "id", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:delete')")
    public CommonResult<Boolean> deleteCourse(@RequestParam("id") Long id) {
        courseService.deleteCourse(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "课程分页列表")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<PageResult<CourseRespVO>> getCoursePage(@Validated CoursePageReqVO pageReqVO) {
        PageResult<CourseDO> pageResult = courseService.getCoursePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CourseRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "课程详情")
    @Parameter(name = "id", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<CourseRespVO> getCourse(@RequestParam("id") Long id) {
        CourseDO course = courseService.getCourse(id);
        return success(BeanUtils.toBean(course, CourseRespVO.class));
    }

    @GetMapping("/version-list")
    @Operation(summary = "课程版本历史（按版本族）")
    @Parameter(name = "courseId", description = "课程 ID（族内任意版本）", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<List<CourseRespVO>> getVersionList(@RequestParam("courseId") Long courseId) {
        List<CourseDO> list = courseService.getVersionList(courseId);
        return success(BeanUtils.toBean(list, CourseRespVO.class));
    }

    @PostMapping("/publish-version")
    @Operation(summary = "发布新版本（复制为草稿，需重新审核）")
    @Parameter(name = "courseId", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Long> publishNewVersion(@RequestParam("courseId") Long courseId) {
        return success(courseService.publishNewVersion(courseId));
    }

}
