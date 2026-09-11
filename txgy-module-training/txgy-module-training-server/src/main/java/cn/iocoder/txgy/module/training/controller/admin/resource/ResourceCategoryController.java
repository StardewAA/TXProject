package cn.iocoder.txgy.module.training.controller.admin.resource;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.CategorySaveReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.CategoryTreeRespVO;
import cn.iocoder.txgy.module.training.service.resource.ResourceCategoryService;
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

@Tag(name = "管理后台 - 资源中心分类")
@RestController
@RequestMapping("/training/resource-category")
@Validated
public class ResourceCategoryController {

    @Resource
    private ResourceCategoryService categoryService;

    @GetMapping("/tree")
    @Operation(summary = "分类树（按类型：doc/video/question）")
    @Parameter(name = "type", description = "分类类型：doc/video/question", required = true, example = "doc")
    // 查询权限与「资源中心 → 分类与标签」菜单权限点保持一致（training:resource-category:query 未在菜单中定义）
    @PreAuthorize("@ss.hasPermission('training:resource:query')")
    public CommonResult<List<CategoryTreeRespVO>> getCategoryTree(@RequestParam("type") String type) {
        return success(categoryService.getCategoryTree(type));
    }

    @PostMapping("/create")
    @Operation(summary = "新增分类")
    @PreAuthorize("@ss.hasPermission('training:resource-category:create')")
    public CommonResult<Long> createCategory(@Valid @RequestBody CategorySaveReqVO reqVO) {
        return success(categoryService.createCategory(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新分类")
    @PreAuthorize("@ss.hasPermission('training:resource-category:update')")
    public CommonResult<Boolean> updateCategory(@Valid @RequestBody CategorySaveReqVO reqVO) {
        categoryService.updateCategory(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除分类（存在子分类或资源时禁止）")
    @Parameter(name = "id", description = "分类 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:resource-category:delete')")
    public CommonResult<Boolean> deleteCategory(@RequestParam("id") Long id) {
        categoryService.deleteCategory(id);
        return success(true);
    }

}
