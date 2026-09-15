package cn.iocoder.txgy.module.cms.controller.app.law;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.cms.api.law.CmsLawApi;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerRespDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSuggestionDTO;
import cn.iocoder.txgy.module.cms.service.rag.CmsRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "用户门户 - CMS 法规检索")
@RestController
@RequestMapping("/cms/law")
@Validated
public class AppCmsLawController {

    private final CmsLawApi lawApi;
    private final CmsRagService ragService;

    public AppCmsLawController(CmsLawApi lawApi, CmsRagService ragService) {
        this.lawApi = lawApi;
        this.ragService = ragService;
    }

    @GetMapping("/search")
    @Operation(summary = "检索已发布的行业法律法规及劳动法")
    public CommonResult<PageResult<CmsLawSearchRespDTO>> search(@Valid CmsLawSearchReqDTO request) {
        return success(lawApi.search(request));
    }

    @GetMapping("/get")
    @Operation(summary = "获得已发布的法规条款详情")
    public CommonResult<CmsLawSearchRespDTO> getArticle(@RequestParam @Positive Long articleId) {
        return success(lawApi.getArticle(articleId));
    }

    @GetMapping("/suggest")
    public CommonResult<List<CmsLawSuggestionDTO>> suggest(@RequestParam String keyword) {
        return success(lawApi.suggest(keyword,5));
    }

    @PostMapping("/answer")
    @Operation(summary = "基于已发布法规原文进行智能回答")
    public CommonResult<CmsLawAnswerRespDTO> answer(@Valid @RequestBody CmsLawAnswerReqDTO request) {
        return success(ragService.answer(request));
    }

}
