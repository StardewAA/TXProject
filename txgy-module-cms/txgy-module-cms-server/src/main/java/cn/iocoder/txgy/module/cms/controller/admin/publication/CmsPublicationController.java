package cn.iocoder.txgy.module.cms.controller.admin.publication;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.cms.service.publication.PublicationUrlParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name="管理后台 - CMS 发布内容解析")
@RestController
@RequestMapping("/cms/publication")
@Validated
public class CmsPublicationController {
    private final PublicationUrlParser parser;
    public CmsPublicationController(PublicationUrlParser parser) {this.parser=parser;}

    @PostMapping("/url/parse")
    @PreAuthorize("@ss.hasPermission('cms:review:submit')")
    @Operation(summary="解析政府网站原文并使用LLM提取待确认元数据")
    public CommonResult<PublicationUrlParser.ParseResult> parseUrl(@RequestBody @Validated UrlRequest request) {
        return success(parser.parse(request.url()));
    }

    public record UrlRequest(@NotBlank @Size(max=2000) String url) { }
}
