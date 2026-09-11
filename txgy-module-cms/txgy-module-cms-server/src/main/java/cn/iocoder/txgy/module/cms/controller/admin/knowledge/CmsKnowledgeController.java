package cn.iocoder.txgy.module.cms.controller.admin.knowledge;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.cms.service.knowledge.CmsKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CMS 知识库")
@RestController
@RequestMapping("/cms/knowledge")
@Validated
public class CmsKnowledgeController {

    private final CmsKnowledgeService knowledgeService;

    public CmsKnowledgeController(CmsKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/list")
    @Operation(summary = "查询当前租户知识库文档")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:query')")
    public CommonResult<List<CmsKnowledgeService.KnowledgeItem>> list() {
        return success(knowledgeService.list());
    }

    @GetMapping("/archive/list")
    @Operation(summary = "查询已软删除的归档内容")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:query')")
    public CommonResult<List<CmsKnowledgeService.KnowledgeItem>> archiveList() {
        return success(knowledgeService.listArchived());
    }

    @PostMapping("/publish")
    @Operation(summary = "发布已审查通过的知识库文档")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<CmsKnowledgeService.PublishResult> publish(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId) {
        return success(knowledgeService.publish(reviewId));
    }

    @DeleteMapping("/unpublish")
    @Operation(summary = "下架知识库文档并删除对应检索数据")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:delete')")
    public CommonResult<Boolean> unpublish(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId) {
        knowledgeService.unpublish(reviewId);
        return success(true);
    }

    @DeleteMapping("/archive")
    @Operation(summary = "软删除已发布内容并移入归档库")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:delete')")
    public CommonResult<Boolean> archive(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId) {
        knowledgeService.archive(reviewId);
        return success(true);
    }

    @PostMapping("/reindex")
    @Operation(summary = "从已审查知识库重建关键词和向量索引")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:reindex')")
    public CommonResult<CmsKnowledgeService.ReindexResult> reindex() {
        return success(knowledgeService.rebuildSearchIndex());
    }
}
