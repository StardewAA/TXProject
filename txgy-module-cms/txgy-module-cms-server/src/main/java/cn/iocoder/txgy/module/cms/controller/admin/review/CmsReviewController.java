package cn.iocoder.txgy.module.cms.controller.admin.review;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.service.review.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;

@RestController
@RequestMapping("/cms/review")
@Tag(name="管理后台 - CMS 文档审查")
@Validated
public class CmsReviewController {
    private final ReviewService service;
    private final ReviewParser parser;
    public CmsReviewController(ReviewService service,ReviewParser parser) {this.service=service;this.parser=parser;}
    private long tenant() {return TenantContextHolder.getRequiredTenantId();}

    @PostMapping("/text")
    @PreAuthorize("@ss.hasPermission('cms:review:submit')")
    public CommonResult<Report> text(@Valid @RequestBody Submission input) {
        return success(service.submit(tenant(),getLoginUserId(),input.metadata(),new Parsed(input.text(),List.of()),"正文.txt",input.text().getBytes(StandardCharsets.UTF_8)));
    }
    @PostMapping(value="/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermission('cms:review:submit')")
    public CommonResult<Report> upload(@RequestPart("file") MultipartFile file,@Valid @RequestPart("metadata") Metadata metadata) throws IOException {
        Parsed parsed=parser.parse(file);
        // Rejected oversized files retain the report but never allocate an unbounded byte array.
        byte[] original=null;
        if(file.getSize()<=ReviewParser.MAX_BYTES) {
            try(var stream=file.getInputStream()) {
                original=stream.readNBytes(ReviewParser.MAX_BYTES+1);
                if(original.length>ReviewParser.MAX_BYTES) original=null;
            }
        }
        return success(service.submit(tenant(),getLoginUserId(),metadata,parsed,file.getOriginalFilename(),original));
    }
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('cms:review:query')")
    public CommonResult<List<Report>> list(@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {return success(service.list(tenant(),limit));}
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('cms:review:query')")
    public CommonResult<Stored> get(@RequestParam @Pattern(regexp="[a-f0-9\\-]{36}") String id) {return success(service.get(tenant(),id));}
    @GetMapping("/original")
    @PreAuthorize("@ss.hasPermission('cms:review:query')")
    public ResponseEntity<byte[]> original(@RequestParam @Pattern(regexp="[a-f0-9\\-]{36}") String id) {
        byte[] bytes=service.original(tenant(),id);
        if(bytes==null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=original-"+id+".bin")
                .header("X-Content-Type-Options","nosniff").body(bytes);
    }
    @PutMapping("/verify")
    @PreAuthorize("@ss.hasPermission('cms:review:verify')")
    public CommonResult<Report> verify(@RequestParam @Pattern(regexp="[a-f0-9\\-]{36}") String id,@Valid @RequestBody EvidenceRequest input) {
        return success(service.verify(tenant(),getLoginUserId(),id,input));
    }
    @PostMapping("/resubmit")
    @PreAuthorize("@ss.hasPermission('cms:review:submit')")
    public CommonResult<Report> resubmit(@RequestParam @Pattern(regexp="[a-f0-9\\-]{36}") String id,@Valid @RequestBody Submission input) {
        return success(service.resubmit(tenant(),getLoginUserId(),id,input));
    }
}
