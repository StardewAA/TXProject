package cn.iocoder.txgy.module.cms.controller.admin.law;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.cms.api.law.CmsLawApi;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerRespDTO;
import cn.iocoder.txgy.module.cms.service.rag.CmsRagService;
import cn.iocoder.txgy.module.cms.service.rag.CmsVectorIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CMS 法规检索")
@RestController
@RequestMapping("/cms/law")
@Validated
public class CmsLawController {

    private final CmsLawApi lawApi;
    private final CmsRagService ragService;
    private final CmsVectorIndexService vectorIndexService;

    public CmsLawController(CmsLawApi lawApi, CmsRagService ragService,
                            CmsVectorIndexService vectorIndexService) {
        this.lawApi = lawApi;
        this.ragService = ragService;
        this.vectorIndexService = vectorIndexService;
    }

    @GetMapping("/search")
    @PreAuthorize("@ss.hasPermission('cms:law:query')")
    @Operation(summary = "检索行业法律法规及劳动法")
    public CommonResult<PageResult<CmsLawSearchRespDTO>> search(@Valid CmsLawSearchReqDTO request) {
        return success(lawApi.search(request));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('cms:law:query')")
    @Operation(summary = "获得法规条款详情")
    @Parameter(name = "articleId", description = "法规条款编号", required = true)
    public CommonResult<CmsLawSearchRespDTO> getArticle(@RequestParam @Positive Long articleId) {
        return success(lawApi.getArticle(articleId));
    }

    @PostMapping("/answer")
    @PreAuthorize("@ss.hasPermission('cms:law:query')")
    @Operation(summary = "基于已入库法规原文进行智能回答")
    public CommonResult<CmsLawAnswerRespDTO> answer(@Valid @RequestBody CmsLawAnswerReqDTO request) {
        return success(ragService.answer(request));
    }

    @PostMapping("/vector/rebuild")
    @PreAuthorize("@ss.hasPermission('cms:knowledge:reindex')")
    @Operation(summary = "重建当前租户法规向量索引")
    public CommonResult<CmsVectorIndexService.RebuildResult> rebuildVectorIndex() {
        return success(vectorIndexService.rebuild());
    }

}
