package cn.iocoder.txgy.module.training.controller.admin.course;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategoryPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategoryRespVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CourseCategorySaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseCategoryDO;
import cn.iocoder.txgy.module.training.service.course.CourseCategoryService;
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

@Tag(name = "管理后台 - 课程分类")
@RestController
@RequestMapping("/training/course-category")
@Validated
public class CourseCategoryController {

    @Resource
    private CourseCategoryService courseCategoryService;

    @PostMapping("/create")
    @Operation(summary = "新增课程分类")
    @PreAuthorize("@ss.hasPermission('training:course:create')")
    public CommonResult<Long> createCategory(@Valid @RequestBody CourseCategorySaveReqVO createReqVO) {
        return success(courseCategoryService.createCategory(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改课程分类")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Boolean> updateCategory(@Valid @RequestBody CourseCategorySaveReqVO updateReqVO) {
        courseCategoryService.updateCategory(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除课程分类（分类下存在课程时返回 409 拦截）")
    @Parameter(name = "id", description = "分类 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:delete')")
    public CommonResult<Boolean> deleteCategory(@RequestParam("id") Long id) {
        courseCategoryService.deleteCategory(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "课程分类分页")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<PageResult<CourseCategoryRespVO>> getCategoryPage(@Validated CourseCategoryPageReqVO pageReqVO) {
        PageResult<CourseCategoryDO> pageResult = courseCategoryService.getCategoryPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CourseCategoryRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "课程分类全量列表（扁平，下拉选择）")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<List<CourseCategoryRespVO>> getCategoryList() {
        List<CourseCategoryDO> list = courseCategoryService.getCategoryList();
        return success(BeanUtils.toBean(list, CourseCategoryRespVO.class));
    }

    @GetMapping("/list-tree")
    @Operation(summary = "课程分类树（域-组-课 三级，分类维护/课程创建级联选择）")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<List<CourseCategoryRespVO>> getCategoryTree() {
        return success(courseCategoryService.getCategoryTree());
    }

    @GetMapping("/get")
    @Operation(summary = "课程分类详情")
    @Parameter(name = "id", description = "分类 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<CourseCategoryRespVO> getCategory(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(courseCategoryService.getCategory(id), CourseCategoryRespVO.class));
    }

}
