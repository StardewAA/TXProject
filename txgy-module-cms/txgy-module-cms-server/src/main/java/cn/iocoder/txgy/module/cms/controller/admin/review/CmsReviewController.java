package cn.iocoder.txgy.module.cms.controller.admin.review;

import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.cms.service.review.*;
import cn.iocoder.txgy.module.cms.service.knowledge.CmsKnowledgeService;
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
import java.util.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.module.cms.service.review.ReviewModels.*;

@RestController
@RequestMapping("/cms/review")
@Tag(name="管理后台 - CMS 文档审查")
@Validated
public class CmsReviewController {
    private static final ErrorCode INVALID_BATCH=new ErrorCode(1_041_001_003,"批量清单与文件不一致，最多20份、合计不超过75MB，文件名不能重复");
    private static final int MAX_BATCH_FILES=20;
    private static final long MAX_BATCH_BYTES=75L*1024*1024;
    private final ReviewService service;
    private final ReviewParser parser;
    private final CmsKnowledgeService knowledgeService;
    public CmsReviewController(ReviewService service,ReviewParser parser,CmsKnowledgeService knowledgeService) {
        this.service=service;this.parser=parser;this.knowledgeService=knowledgeService;
    }
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
    @PostMapping(value="/batch-upload", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermission('cms:review:submit')")
    public CommonResult<BatchResult> batchUpload(@RequestPart("files") List<MultipartFile> files,
                                                  @Valid @RequestPart("manifest") BatchManifest manifest) throws IOException {
        if (files == null || files.isEmpty() || files.size() > MAX_BATCH_FILES || manifest.items().size() != files.size()) {
            throw exception(INVALID_BATCH);
        }
        long total = 0;
        Map<String, MultipartFile> byName = new LinkedHashMap<>();
        for (MultipartFile file : files) {
            String name = cleanName(file.getOriginalFilename());
            total = Math.addExact(total, Math.max(0, file.getSize()));
            if (name == null || byName.putIfAbsent(name, file) != null) throw exception(INVALID_BATCH);
        }
        if (total > MAX_BATCH_BYTES) throw exception(INVALID_BATCH);
        Set<String> manifestNames = new HashSet<>();
        List<BatchDocument> documents = new ArrayList<>(files.size());
        for (BatchItem item : manifest.items()) {
            String name = cleanName(item.fileName());
            MultipartFile file = name == null ? null : byName.get(name);
            if (file == null || !manifestNames.add(name)) throw exception(INVALID_BATCH);
            Parsed parsed = parser.parse(file);
            byte[] original = null;
            if (file.getSize() <= ReviewParser.MAX_BYTES) {
                try (var stream = file.getInputStream()) {
                    original = stream.readNBytes(ReviewParser.MAX_BYTES + 1);
                    if (original.length > ReviewParser.MAX_BYTES) original = null;
                }
            }
            documents.add(new BatchDocument(name, item.metadata(), parsed, original));
        }
        if (!manifestNames.equals(byName.keySet())) throw exception(INVALID_BATCH);
        return success(service.submitBatch(tenant(), getLoginUserId(), List.copyOf(documents)));
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
        Report report=service.verify(tenant(),getLoginUserId(),id,input);
        knowledgeService.publish(id);
        return success(report);
    }
    @PostMapping("/duplicate-check")
    @PreAuthorize("@ss.hasPermission('cms:review:submit')")
    public CommonResult<DuplicateCheckResult> duplicateCheck(@Valid @RequestBody DuplicateCheckRequest input) {
        return success(service.checkDuplicate(tenant(),input.metadata(),input.text()));
    }
    @PutMapping("/manual-review")
    @PreAuthorize("@ss.hasPermission('cms:review:verify')")
    public CommonResult<Report> manualReview(@RequestParam @Pattern(regexp="[a-f0-9\\-]{36}") String id,
                                              @Valid @RequestBody ManualReviewRequest input) {
        Report report=service.manualReview(tenant(),getLoginUserId(),id,input);
        if(input.decision()==ManualDecision.APPROVE) knowledgeService.publish(id);
        return success(report);
    }
    @PostMapping("/resubmit")
    @PreAuthorize("@ss.hasPermission('cms:review:submit')")
    public CommonResult<Report> resubmit(@RequestParam @Pattern(regexp="[a-f0-9\\-]{36}") String id,@Valid @RequestBody Submission input) {
        return success(service.resubmit(tenant(),getLoginUserId(),id,input));
    }

    private static String cleanName(String raw) {
        if (raw == null) return null;
        String value = raw.strip();
        int split = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String name = value.substring(split + 1).strip();
        return name.isEmpty() || name.length() > 255 ? null : name;
    }
}
