package cn.iocoder.txgy.module.training.controller.admin.course;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseMediaSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseMediaDO;
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

@Tag(name = "管理后台 - 课程媒资绑定")
@RestController
@RequestMapping("/training/course-media")
@Validated
public class CourseMediaController {

    @Resource
    private CourseService courseService;

    @PostMapping("/create")
    @Operation(summary = "绑定媒资到课程（文档/视频/图片）")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Long> createMedia(@Valid @RequestBody CourseMediaSaveReqVO createReqVO) {
        return success(courseService.createMedia(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改课程媒资")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Boolean> updateMedia(@Valid @RequestBody CourseMediaSaveReqVO updateReqVO) {
        courseService.updateMedia(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除课程媒资")
    @Parameter(name = "id", description = "媒资 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Boolean> deleteMedia(@RequestParam("id") Long id) {
        courseService.deleteMedia(id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "课程媒资列表")
    @Parameter(name = "courseId", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<List<CourseMediaDO>> getMediaList(@RequestParam("courseId") Long courseId) {
        return success(courseService.getMediaList(courseId));
    }

    @PutMapping("/sort")
    @Operation(summary = "媒资排序（按传入顺序重排，仅草稿可调整）")
    @Parameter(name = "courseId", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Boolean> sortMedia(@RequestParam("courseId") Long courseId,
                                           @RequestBody List<Long> mediaIds) {
        courseService.sortMedia(courseId, mediaIds);
        return success(true);
    }

}
