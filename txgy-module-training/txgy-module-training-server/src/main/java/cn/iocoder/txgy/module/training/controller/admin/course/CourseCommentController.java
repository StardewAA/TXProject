package cn.iocoder.txgy.module.training.controller.admin.course;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCommentPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCommentSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseCommentDO;
import cn.iocoder.txgy.module.training.service.course.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 课程评论治理")
@RestController
@RequestMapping("/training/course-comment")
@Validated
public class CourseCommentController {

    @Resource
    private CourseService courseService;

    @PostMapping("/create")
    @Operation(summary = "发表课程评论（仅已上架课程可评论）")
    @PreAuthorize("@ss.hasPermission('training:course:comment')")
    public CommonResult<Long> createComment(@Valid @RequestBody CourseCommentSaveReqVO createReqVO) {
        return success(courseService.createComment(createReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除课程评论（学员删除自己的评论）")
    @Parameter(name = "id", description = "评论 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:delete')")
    public CommonResult<Boolean> deleteComment(@RequestParam("id") Long id) {
        courseService.deleteComment(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "课程评论分页")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<PageResult<CourseCommentDO>> getCommentPage(@Validated CourseCommentPageReqVO pageReqVO) {
        return success(courseService.getCommentPage(pageReqVO));
    }

}
