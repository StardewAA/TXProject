package cn.iocoder.txgy.module.training.controller.portal.course;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCommentPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCommentSaveReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CoursePageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseRespVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategoryRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseCommentDO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.enums.course.CourseStatusEnum;
import cn.iocoder.txgy.module.training.service.course.CourseService;
import cn.iocoder.txgy.module.training.service.course.CourseCategoryService;
import cn.iocoder.txgy.module.training.service.course.external.CourseExternalApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

import java.util.List;

/**
 * 学员端（Portal）课程接口
 *
 * <p>供学员演示端消费：仅展示已上架课程与正常可见评论；发表/删除评论与学习登记需登录。
 * 正式学员端（模块五）可复用本接口或按端侧协议二次包装。</p>
 *
 * @author 云培训学院课题小组
 */
@Tag(name = "学员端 - 培训课程")
@RestController
@RequestMapping("/training/portal/course")
@Validated
public class PortalCourseController {

    /** 个人学员（tenant_id=0）读取集团统一维护的课程目录。 */
    private static final Long PLATFORM_CATALOG_TENANT_ID = 1L;

    @Resource
    private CourseService courseService;
    @Resource
    private CourseCategoryService courseCategoryService;
    @Resource
    private CourseExternalApiService courseExternalApiService;

    @GetMapping("/page")
    @Operation(summary = "已上架课程分页列表（学员可见）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<CourseRespVO>> getCoursePage(@Validated CoursePageReqVO pageReqVO) {
        pageReqVO.setStatus(CourseStatusEnum.ON_SHELF.getStatus());
        pageReqVO.setIsCurrent(Boolean.TRUE);
        PageResult<CourseDO> pageResult = executeInCatalogTenant(() -> courseService.getCoursePage(pageReqVO));
        return success(BeanUtils.toBean(pageResult, CourseRespVO.class));
    }

    @GetMapping("/category-tree")
    @Operation(summary = "学员端课程分类树")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<CourseCategoryRespVO>> getCategoryTree() {
        return success(executeInCatalogTenant(() -> courseCategoryService.getCategoryTree()));
    }

    @GetMapping("/get")
    @Operation(summary = "课程详情（仅已上架）")
    @Parameter(name = "id", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<CourseRespVO> getCourse(@RequestParam("id") Long id) {
        CourseDO course = executeInCatalogTenant(() -> courseService.getCourse(id));
        return success(BeanUtils.toBean(course, CourseRespVO.class));
    }

    private <T> T executeInCatalogTenant(java.util.concurrent.Callable<T> callable) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && tenantId == 0L) {
            return TenantUtils.execute(PLATFORM_CATALOG_TENANT_ID, callable);
        }
        try {
            return callable.call();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @GetMapping("/comment/page")
    @Operation(summary = "课程评论分页（仅正常可见评论）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<CourseCommentDO>> getCommentPage(@Validated CourseCommentPageReqVO pageReqVO) {
        pageReqVO.setStatus(1);
        return success(courseService.getCommentPage(pageReqVO));
    }

    @PostMapping("/comment/create")
    @Operation(summary = "发表课程评论（仅已上架课程可评论）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Long> createComment(@Valid @RequestBody CourseCommentSaveReqVO createReqVO) {
        return success(courseService.createComment(createReqVO));
    }

    @PostMapping("/comment/delete")
    @Operation(summary = "删除自己的评论")
    @Parameter(name = "id", description = "评论 ID", required = true, example = "1")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> deleteMyComment(@RequestParam("id") Long id) {
        courseService.deleteMyComment(id);
        return success(true);
    }

    @PostMapping("/learning/register")
    @Operation(summary = "登记学习记录（占位：正式由模块五学员端学习记录承载）")
    @Parameter(name = "courseId", description = "课程 ID", required = true, example = "1")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Long> registerLearning(@RequestParam("courseId") Long courseId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        String nickname = SecurityFrameworkUtils.getLoginUserNickname();
        courseExternalApiService.addLearningRef(courseId, userId, nickname == null ? "学员" : nickname);
        return success(courseId);
    }

}
