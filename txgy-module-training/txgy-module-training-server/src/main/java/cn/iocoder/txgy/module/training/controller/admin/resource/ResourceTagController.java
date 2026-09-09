package cn.iocoder.txgy.module.training.controller.admin.resource;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.ResourceTagPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.ResourceTagSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.ResourceTagDO;
import cn.iocoder.txgy.module.training.service.resource.ResourceTagService;
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

@Tag(name = "管理后台 - 资源标签")
@RestController
@RequestMapping("/training/resource-tag")
@Validated
public class ResourceTagController {

    @Resource
    private ResourceTagService tagService;

    @GetMapping("/list")
    @Operation(summary = "标签列表（可按维度过滤）")
    @Parameter(name = "dimension", description = "维度：work_type/course/safety/process，空则全量", example = "safety")
    @PreAuthorize("@ss.hasPermission('training:resource-category:query')")
    public CommonResult<List<ResourceTagDO>> getTagList(@RequestParam(value = "dimension", required = false) String dimension) {
        return success(tagService.getTagList(dimension));
    }

    @GetMapping("/page")
    @Operation(summary = "标签分页")
    @PreAuthorize("@ss.hasPermission('training:resource-category:query')")
    public CommonResult<PageResult<ResourceTagDO>> getTagPage(@Validated ResourceTagPageReqVO pageReqVO) {
        return success(tagService.getTagPage(pageReqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "新增标签")
    @PreAuthorize("@ss.hasPermission('training:resource-category:create')")
    public CommonResult<Long> createTag(@Valid @RequestBody ResourceTagSaveReqVO reqVO) {
        return success(tagService.createTag(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新标签")
    @PreAuthorize("@ss.hasPermission('training:resource-category:update')")
    public CommonResult<Boolean> updateTag(@Valid @RequestBody ResourceTagSaveReqVO reqVO) {
        tagService.updateTag(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除标签（已被试题引用时禁止）")
    @Parameter(name = "id", description = "标签 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:resource-category:delete')")
    public CommonResult<Boolean> deleteTag(@RequestParam("id") Long id) {
        tagService.deleteTag(id);
        return success(true);
    }

}
