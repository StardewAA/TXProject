package cn.iocoder.txgy.module.training.controller.admin.resource;

import cn.hutool.core.io.IoUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.DocumentPageCountRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.DocumentPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.DocumentResourceDO;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.service.resource.DocumentResourceService;
import cn.iocoder.txgy.module.training.service.resource.ResourceFileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 文档资源库")
@RestController
@RequestMapping("/training/document")
@Validated
public class DocumentResourceController {

    @Resource
    private DocumentResourceService documentService;
    @Resource
    private ResourceFileStorageService fileStorage;

    @PostMapping("/upload")
    @Operation(summary = "上传文档（PDF/JPG/PNG ≤50MB，成功即上线）")
    @PreAuthorize("@ss.hasPermission('training:resource-doc:create')")
    public CommonResult<Long> uploadDocument(@RequestParam("file") MultipartFile file,
                                             @RequestParam("title") String title,
                                             @RequestParam(value = "categoryId", required = false) Long categoryId,
                                             @RequestParam(value = "bizCategory", required = false) String bizCategory,
                                             @RequestParam(value = "tags", required = false) String tags,
                                             @RequestParam(value = "estMinutes", required = false) Integer estMinutes) {
        return success(documentService.uploadDocument(file, title, categoryId, bizCategory, tags, estMinutes));
    }

    @PostMapping("/page-count")
    @Operation(summary = "识别文档页数（自动换算预估阅读时长 = 页数 × 10 分钟/页）")
    @PreAuthorize("@ss.hasPermission('training:resource-doc:create')")
    public CommonResult<DocumentPageCountRespVO> countDocumentPages(@RequestParam("file") MultipartFile file) {
        return success(documentService.countDocumentPages(file));
    }

    @GetMapping("/page")
    @Operation(summary = "文档分页列表")
    @PreAuthorize("@ss.hasPermission('training:resource-doc:query')")
    public CommonResult<PageResult<DocumentResourceDO>> getDocumentPage(@Validated DocumentPageReqVO pageReqVO) {
        return success(documentService.getDocumentPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "文档详情")
    @Parameter(name = "id", description = "文档资源 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:resource-doc:query')")
    public CommonResult<DocumentResourceDO> getDocument(@RequestParam("id") Long id) {
        return success(documentService.getDocument(id));
    }

    @PutMapping("/update")
    @Operation(summary = "更新文档元数据")
    @PreAuthorize("@ss.hasPermission('training:resource-doc:update')")
    public CommonResult<Boolean> updateDocument(@RequestParam("id") Long id,
                                                @RequestParam("title") String title,
                                                @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                @RequestParam(value = "bizCategory", required = false) String bizCategory,
                                                @RequestParam(value = "tags", required = false) String tags,
                                                @RequestParam(value = "estMinutes", required = false) Integer estMinutes) {
        documentService.updateDocument(id, title, categoryId, bizCategory, tags, estMinutes);
        return success(true);
    }

    @PostMapping("/status")
    @Operation(summary = "文档状态流转：DRAFT草稿/ONLINE上线/OFFLINE下架/ARCHIVED归档")
    @Parameter(name = "id", description = "文档资源 ID", required = true, example = "1")
    @Parameter(name = "action", description = "动作：ONLINE/OFFLINE/ARCHIVE/DRAFT", required = true, example = "OFFLINE")
    @PreAuthorize("@ss.hasPermission('training:resource-doc:update')")
    public CommonResult<Boolean> changeDocumentStatus(@RequestParam("id") Long id,
                                                      @RequestParam("action") String action) {
        documentService.changeDocumentStatus(id, action);
        return success(true);
    }

    @PostMapping("/reading")
    @Operation(summary = "阅读上报（预览次数+1、停留秒数累加）")
    @PreAuthorize("@ss.hasPermission('training:resource-doc:query')")
    public CommonResult<Boolean> reportReading(@RequestParam("id") Long id,
                                               @RequestParam(value = "seconds", required = false) Integer seconds) {
        documentService.reportReading(id, seconds);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文档（被引用时禁止删除）")
    @Parameter(name = "id", description = "文档资源 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:resource-doc:delete')")
    public CommonResult<Boolean> deleteDocument(@RequestParam("id") Long id) {
        documentService.deleteDocument(id);
        return success(true);
    }

    // ========== 在线预览（内联直出，匿名可访问） ==========

    @GetMapping("/preview")
    @Operation(summary = "文档在线预览（inline 直出，PDF 浏览器内打开）")
    @PermitAll
    @TenantIgnore
    public void previewDocument(@RequestParam("id") Long id, HttpServletResponse response) throws Exception {
        DocumentResourceDO document = documentService.getDocument(id);
        File file = fileStorage.loadFile(document.getFilePath());
        if (file == null || !file.exists()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_FILE_NOT_FOUND);
        }
        response.setContentType(ResourceFileStorageService.contentTypeOf(document.getFileType()));
        String encodedName = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''" + encodedName);
        response.setContentLengthLong(file.length());
        try (InputStream in = new FileInputStream(file)) {
            IoUtil.copy(in, response.getOutputStream());
            response.getOutputStream().flush();
        }
    }

}
