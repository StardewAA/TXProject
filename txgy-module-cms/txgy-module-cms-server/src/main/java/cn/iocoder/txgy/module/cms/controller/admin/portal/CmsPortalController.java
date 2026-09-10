package cn.iocoder.txgy.module.cms.controller.admin.portal;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.api.law.CmsLawApi;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerRespDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.service.rag.CmsRagService;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels;
import cn.iocoder.txgy.module.cms.service.review.ReviewParser;
import cn.iocoder.txgy.module.cms.service.review.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** 企业门户 CMS：只开放已发布内容检索和待审发布申请。 */
@Tag(name = "企业门户 - CMS 检索与发布申请")
@RestController
@RequestMapping("/cms/portal")
@Validated
@PreAuthorize("isAuthenticated()")
public class CmsPortalController {

    private final CmsLawApi lawApi;
    private final CmsRagService ragService;
    private final ReviewService reviewService;
    private final ReviewParser reviewParser;

    public CmsPortalController(CmsLawApi lawApi, CmsRagService ragService,
                               ReviewService reviewService, ReviewParser reviewParser) {
        this.lawApi = lawApi;
        this.ragService = ragService;
        this.reviewService = reviewService;
        this.reviewParser = reviewParser;
    }

    @GetMapping("/law/search")
    @Operation(summary = "检索已审查且已发布的法规原文")
    public CommonResult<PageResult<CmsLawSearchRespDTO>> search(@Valid CmsLawSearchReqDTO request) {
        return success(lawApi.search(request));
    }

    @PostMapping("/law/answer")
    @Operation(summary = "基于已发布原文生成带引用的智能回答")
    public CommonResult<CmsLawAnswerRespDTO> answer(@Valid @RequestBody CmsLawAnswerReqDTO request) {
        return success(ragService.answer(request));
    }

    @PostMapping("/submission/text")
    @Operation(summary = "提交文本发布申请，自动进入审查")
    public CommonResult<ReviewModels.Report> submitText(@Valid @RequestBody ReviewModels.Submission input) {
        byte[] original = input.text().getBytes(StandardCharsets.UTF_8);
        return success(reviewService.submit(tenant(), user(), input.metadata(),
                new ReviewModels.Parsed(input.text(), List.of()), "正文.txt", original));
    }

    @PostMapping(value = "/submission/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档发布申请，自动进入审查")
    public CommonResult<ReviewModels.Report> submitFile(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") ReviewModels.Metadata metadata) throws IOException {
        ReviewModels.Parsed parsed = reviewParser.parse(file);
        byte[] original = null;
        if (file.getSize() <= ReviewParser.MAX_BYTES) {
            try (var stream = file.getInputStream()) {
                original = stream.readNBytes(ReviewParser.MAX_BYTES + 1);
                if (original.length > ReviewParser.MAX_BYTES) original = null;
            }
        }
        return success(reviewService.submit(tenant(), user(), metadata, parsed,
                file.getOriginalFilename(), original));
    }

    @GetMapping("/submission/mine")
    @Operation(summary = "查询当前企业用户的发布申请")
    public CommonResult<List<ReviewModels.SubmissionStatus>> mine(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return success(reviewService.listBySubmitter(tenant(), user(), limit));
    }

    private long tenant() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private long user() {
        return getLoginUserId();
    }
}
