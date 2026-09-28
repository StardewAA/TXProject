package cn.iocoder.txgy.module.cms.controller.admin.publication;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.cms.service.publication.PublicationUrlParser;
import cn.iocoder.txgy.module.cms.service.storage.CmsUrlSnapshotService;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
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
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name="管理后台 - CMS 发布内容解析")
@RestController
@RequestMapping("/cms/publication")
@Validated
public class CmsPublicationController {
    private final PublicationUrlParser parser;
    private final CmsUrlSnapshotService snapshotService;
    public CmsPublicationController(PublicationUrlParser parser, CmsUrlSnapshotService snapshotService) {
        this.parser=parser;
        this.snapshotService=snapshotService;
    }

    @PostMapping("/url/parse")
    @PreAuthorize("@ss.hasPermission('cms:review:submit')")
    @Operation(summary="解析政府网站原文并使用LLM提取待确认元数据")
    public CommonResult<PublicationUrlParser.ParseResult> parseUrl(@RequestBody @Validated UrlRequest request) {
        PublicationUrlParser.ParseResult result=parser.parse(request.url());
        String token=snapshotService.stage(TenantContextHolder.getRequiredTenantId(),getLoginUserId(),
                result.metadata().title(),result.metadata().sourceUrl(),result.text());
        return success(result.withSnapshotToken(token));
    }

    public record UrlRequest(@NotBlank @Size(max=2000) String url) { }
}
