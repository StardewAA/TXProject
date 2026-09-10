package cn.iocoder.txgy.module.training.controller.admin.course;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseAuditLogPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseAuditReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CoursePageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseAuditLogDO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.enums.course.CourseStatusEnum;
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

@Tag(name = "管理后台 - 课程审核工作台")
@RestController
@RequestMapping("/training/course-audit")
@Validated
public class CourseAuditController {

    @Resource
    private CourseService courseService;

    @PostMapping("/submit")
    @Operation(summary = "提交审核（草稿 → 待审核）")
    @Parameter(name = "courseId", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Boolean> submit(@RequestParam("courseId") Long courseId) {
        courseService.submit(courseId);
        return success(true);
    }

    @PostMapping("/withdraw")
    @Operation(summary = "撤审（待审核 → 草稿）")
    @Parameter(name = "courseId", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Boolean> withdraw(@RequestParam("courseId") Long courseId) {
        courseService.withdraw(courseId);
        return success(true);
    }

    @PostMapping("/approve")
    @Operation(summary = "审核通过（待审核 → 审核通过/待上架）")
    @PreAuthorize("@ss.hasPermission('training:course:audit')")
    public CommonResult<Boolean> approve(@Valid @RequestBody CourseAuditReqVO reqVO) {
        courseService.approve(reqVO.getCourseId());
        return success(true);
    }

    @PostMapping("/reject")
    @Operation(summary = "驳回（待审核 → 草稿，原因必填）")
    @PreAuthorize("@ss.hasPermission('training:course:audit')")
    public CommonResult<Boolean> reject(@Valid @RequestBody CourseAuditReqVO reqVO) {
        courseService.reject(reqVO.getCourseId(), reqVO.getReason());
        return success(true);
    }

    @PostMapping("/online")
    @Operation(summary = "上架（审核通过 → 已上架，需已绑定媒资）")
    @Parameter(name = "courseId", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Boolean> online(@RequestParam("courseId") Long courseId) {
        courseService.online(courseId);
        return success(true);
    }

    @PostMapping("/offline")
    @Operation(summary = "下架（已上架 → 已下架）")
    @Parameter(name = "courseId", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Boolean> offline(@RequestParam("courseId") Long courseId) {
        courseService.offline(courseId);
        return success(true);
    }

    @GetMapping("/logs/page")
    @Operation(summary = "审核日志分页（永久留痕）")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<PageResult<CourseAuditLogDO>> getAuditLogPage(@Validated CourseAuditLogPageReqVO pageReqVO) {
        return success(courseService.getAuditLogPage(pageReqVO));
    }

    @GetMapping("/queue")
    @Operation(summary = "审核队列（待审核课程分页，供审核员轮询处理）")
    @PreAuthorize("@ss.hasPermission('training:course:audit')")
    public CommonResult<PageResult<CourseRespVO>> getAuditQueue(@Validated CoursePageReqVO pageReqVO) {
        pageReqVO.setStatus(CourseStatusEnum.PENDING_REVIEW.getStatus());
        pageReqVO.setIsCurrent(Boolean.TRUE);
        PageResult<CourseDO> pageResult = courseService.getCoursePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CourseRespVO.class));
    }

}
