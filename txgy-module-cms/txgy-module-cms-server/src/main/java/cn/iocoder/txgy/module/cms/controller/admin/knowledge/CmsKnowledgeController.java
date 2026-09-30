package cn.iocoder.txgy.module.cms.controller.admin.knowledge;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.cms.service.knowledge.CmsKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/archive/restore")
    @Operation(summary = "恢复归档内容并重新加入检索知识库")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<CmsKnowledgeService.PublishResult> restore(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId) {
        return success(knowledgeService.restore(reviewId));
    }

    @DeleteMapping("/permanent-delete")
    @Operation(summary = "彻底删除已驳回或已归档内容及其审查历史")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:delete')")
    public CommonResult<Boolean> permanentDelete(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId) {
        knowledgeService.permanentlyDelete(reviewId);
        return success(true);
    }

    @PostMapping("/reindex")
    @Operation(summary = "从已审查知识库重建关键词和向量索引")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:reindex')")
    public CommonResult<CmsKnowledgeService.ReindexResult> reindex() {
        return success(knowledgeService.rebuildSearchIndex());
    }

    @GetMapping("/chunk/list")
    @Operation(summary = "查询文档的完整切片列表")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:query')")
    public CommonResult<List<CmsKnowledgeService.KnowledgeChunk>> listChunks(
            @RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId) {
        return success(knowledgeService.listChunks(reviewId));
    }

    @PutMapping("/chunk/update")
    @Operation(summary = "人工修正切片并同步向量与检索索引")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<CmsKnowledgeService.KnowledgeChunk> updateChunk(@Valid @RequestBody UpdateChunkRequest request) {
        return success(knowledgeService.updateChunk(request.reviewId(), request.ordinal(),
                request.content(), request.version()));
    }

    @PostMapping("/chunk/create")
    @Operation(summary = "人工补充切片并同步检索索引")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<CmsKnowledgeService.KnowledgeChunk> createChunk(@Valid @RequestBody CreateChunkRequest request) {
        return success(knowledgeService.addChunk(request.reviewId(), request.content(), request.pageNumber()));
    }

    @DeleteMapping("/chunk/delete")
    @Operation(summary = "删除切片并同步清理检索索引")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:publish')")
    public CommonResult<Boolean> deleteChunk(@RequestParam @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId,
                                             @RequestParam @Min(1) int ordinal,
                                             @RequestParam @Min(0) long version) {
        knowledgeService.deleteChunk(reviewId, ordinal, version);
        return success(true);
    }

    public record UpdateChunkRequest(
            @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId,
            @Min(1) int ordinal,
            @NotBlank @Size(max = 100_000) String content,
            @Min(0) long version) { }

    public record CreateChunkRequest(
            @Pattern(regexp = "[a-f0-9\\-]{36}") String reviewId,
            @NotBlank @Size(max = 100_000) String content,
            @Min(1) @Max(100_000) Integer pageNumber) { }
}
