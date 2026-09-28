package cn.iocoder.txgy.module.cms.controller.admin.contentext;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.cms.service.contentext.CmsContentExtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/** 内容管理扩展：栏目树、素材库、推荐位、SEO、审查任务。 */
@Tag(name = "管理后台 - CMS 内容管理扩展")
@RestController
@RequestMapping("/cms/content-ext")
@Validated
public class CmsContentExtController {

    private final CmsContentExtService service;

    public CmsContentExtController(CmsContentExtService service) {
        this.service = service;
    }

    // ==================== 栏目树 ====================

    @GetMapping("/category/tree")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "栏目树（按当前用户角色过滤）")
    public CommonResult<List<Map<String, Object>>> categoryTree() {
        return success(service.categoryTree());
    }

    @PostMapping("/category/save")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "新增或更新栏目")
    public CommonResult<Long> saveCategory(@Valid @RequestBody CmsContentExtService.CategoryInput input) {
        return success(service.saveCategory(input));
    }

    @DeleteMapping("/category/delete")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "删除栏目（存在子栏目时禁止）")
    public CommonResult<Boolean> deleteCategory(@RequestParam Long id) {
        service.deleteCategory(id);
        return success(true);
    }

    // ==================== 素材库 ====================

    @GetMapping("/asset/page")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "素材分页")
    public CommonResult<Map<String, Object>> assetPage(@RequestParam(required = false) Integer pageNo,
                                                       @RequestParam(required = false) Integer pageSize,
                                                       @RequestParam(required = false) String assetType,
                                                       @RequestParam(required = false) String keyword) {
        return success(service.assetPage(pageNo, pageSize, assetType, keyword));
    }

    @PostMapping("/asset/save")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "保存素材元信息（文件由平台文件服务上传）")
    public CommonResult<Long> saveAsset(@Valid @RequestBody CmsContentExtService.AssetInput input) {
        return success(service.saveAsset(input));
    }

    @DeleteMapping("/asset/delete")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "删除素材")
    public CommonResult<Boolean> deleteAsset(@RequestParam Long id) {
        service.deleteAsset(id);
        return success(true);
    }

    // ==================== 推荐位 ====================

    @GetMapping("/recommend/page")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "推荐位分页")
    public CommonResult<Map<String, Object>> recommendPage(@RequestParam(required = false) Integer pageNo,
                                                           @RequestParam(required = false) Integer pageSize,
                                                           @RequestParam(required = false) String position) {
        return success(service.recommendPage(pageNo, pageSize, position));
    }

    @PostMapping("/recommend/save")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "新增或更新推荐位")
    public CommonResult<Long> saveRecommend(@Valid @RequestBody CmsContentExtService.RecommendInput input) {
        return success(service.saveRecommend(input));
    }

    @DeleteMapping("/recommend/delete")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "删除推荐位")
    public CommonResult<Boolean> deleteRecommend(@RequestParam Long id) {
        service.deleteRecommend(id);
        return success(true);
    }

    // ==================== SEO ====================

    @GetMapping("/seo/get")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "读取 SEO 元信息")
    public CommonResult<Map<String, Object>> getSeo(@RequestParam String contentType,
                                                   @RequestParam Long contentId) {
        return success(service.getSeo(contentType, contentId));
    }

    @PostMapping("/seo/save")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    @Operation(summary = "保存 SEO 元信息")
    public CommonResult<Long> saveSeo(@Valid @RequestBody CmsContentExtService.SeoInput input) {
        return success(service.saveSeo(input));
    }

    // ==================== 审查任务 ====================

    @GetMapping("/review-task/page")
    @PreAuthorize("@ss.hasPermission('cms:review:query')")
    @Operation(summary = "审查任务分页")
    public CommonResult<Map<String, Object>> reviewTaskPage(@RequestParam(required = false) String status,
                                                            @RequestParam(required = false) Long assigneeId,
                                                            @RequestParam(required = false) Integer pageNo,
                                                            @RequestParam(required = false) Integer pageSize) {
        return success(service.reviewTaskPage(status, assigneeId, pageNo, pageSize));
    }

    @PostMapping("/review-task/assign")
    @PreAuthorize("@ss.hasPermission('cms:review:verify')")
    @Operation(summary = "分配或转交审查任务")
    public CommonResult<Long> assignTask(@Valid @RequestBody CmsContentExtService.AssignInput input) {
        return success(service.assignTask(input));
    }

    @PostMapping("/review-task/complete")
    @PreAuthorize("@ss.hasPermission('cms:review:verify')")
    @Operation(summary = "标记审查任务完成")
    public CommonResult<Boolean> completeTask(@RequestParam Long taskId) {
        service.completeTask(taskId);
        return success(true);
    }

    @GetMapping("/review-task/overdue-scan")
    @PreAuthorize("@ss.hasPermission('cms:review:verify')")
    @Operation(summary = "手动触发审查任务超时扫描")
    public CommonResult<Integer> scanOverdue() {
        return success(service.markOverdue());
    }
}