package cn.iocoder.txgy.module.infra.controller.admin.file;

import cn.hutool.core.io.IoUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.infra.controller.admin.file.vo.file.*;
import cn.iocoder.txgy.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.txgy.module.infra.service.file.FileService;
import cn.iocoder.txgy.module.infra.service.file.OssPromoteRetryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 文件存储")
@RestController
@RequestMapping("/infra/file")
@Validated
public class FileController {

    @Resource
    private FileService fileService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "模式一：后端上传文件")
    @Parameter(name = "file", description = "文件附件", required = true,
            schema = @Schema(type = "string", format = "binary"))
    public CommonResult<String> uploadFile(@Valid FileUploadReqVO uploadReqVO) throws Exception {
        MultipartFile file = uploadReqVO.getFile();
        byte[] content = IoUtil.readBytes(file.getInputStream());
        return success(fileService.createFile(content, file.getOriginalFilename(),
                uploadReqVO.getDirectory(), file.getContentType()));
    }

    @GetMapping("/presigned-url")
    @Operation(summary = "获取文件预签名地址（上传）", description = "模式二：前端上传文件：用于前端直接上传七牛、阿里云 OSS 等文件存储器")
    @Parameters({
            @Parameter(name = "name", description = "文件名称", required = true),
            @Parameter(name = "directory", description = "文件目录")
    })
    public CommonResult<FilePresignedUrlRespVO> getFilePresignedUrl(
            @RequestParam("name") String name,
            @RequestParam(value = "directory", required = false) String directory) {
        return success(fileService.presignPutUrl(name, directory));
    }

    @PostMapping("/create")
    @Operation(summary = "创建文件", description = "模式二：前端上传文件：配合 presigned-url 接口，记录上传了上传的文件")
    public CommonResult<Long> createFile(@Valid @RequestBody FileCreateReqVO createReqVO) {
        return success(fileService.createFile(createReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获得文件")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public CommonResult<FileRespVO> getFile(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(fileService.getFile(id), FileRespVO.class));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文件")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('infra:file:delete')")
    public CommonResult<Boolean> deleteFile(@RequestParam("id") Long id) throws Exception {
        fileService.deleteFile(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除文件")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('infra:file:delete')")
    public CommonResult<Boolean> deleteFileList(@RequestParam("ids") List<Long> ids) throws Exception {
        fileService.deleteFileList(ids);
        return success(true);
    }

    @PostMapping("/clean-expired-orphan")
    @Operation(summary = "清理过期的待绑定孤儿文件",
            description = "删除超过待绑定可读窗口、且始终未被业务单据绑定的私有文件（记录 + 存储对象）")
    @Parameter(name = "limit", description = "单次最大清理条数")
    @PreAuthorize("@ss.hasPermission('infra:file:delete')")
    public CommonResult<Integer> cleanExpiredOrphanFiles(
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit) {
        return success(fileService.cleanExpiredOrphanFiles(limit));
    }

    @GetMapping("/{configId}/get/**")
    @TenantIgnore
    @Operation(summary = "下载文件",
            description = "公开资源匿名可读；私有资源需登录且通过归属/参与方校验（无法确认归属时拒绝）")
    @Parameter(name = "configId", description = "配置编号", required = true)
    public void getFileContent(HttpServletRequest request,
                               HttpServletResponse response,
                               @PathVariable("configId") Long configId) throws Exception {
        fileService.handleReadRequest(request, response, configId, false);
    }

    @GetMapping("/{configId}/preview/**")
    @TenantIgnore
    @Operation(summary = "在线预览文件",
            description = "公开资源匿名可读；私有资源需登录且通过归属/参与方校验（无法确认归属时拒绝）")
    @Parameter(name = "configId", description = "配置编号", required = true)
    public void previewFileContent(HttpServletRequest request,
                                   HttpServletResponse response,
                                   @PathVariable("configId") Long configId) throws Exception {
        fileService.handleReadRequest(request, response, configId, true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得文件分页")
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public CommonResult<PageResult<FileRespVO>> getFilePage(@Valid FilePageReqVO pageVO) {
        PageResult<FileDO> pageResult = fileService.getFilePage(pageVO);
        return success(BeanUtils.toBean(pageResult, FileRespVO.class));
    }

    @PostMapping("/retry-oss-promotion")
    @Operation(summary = "重试 OSS 提升补传",
            description = "把「已确认公开但未进入公开 OSS」的文件补传到公共桶；可指定 fileId 单文件补传，"
                    + "或不传 fileId 批量补传全部 stranded 公开文件。仅处理 PUBLIC 文件，私有文件不会被迁移。")
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public CommonResult<FileOssPromoteRetryRespVO> retryOssPromotion(
            @RequestParam(value = "fileId", required = false) Long fileId,
            @RequestParam(value = "limit", defaultValue = "200") Integer limit) {
        OssPromoteRetryResult result = fileId != null
                ? fileService.retryOssPromotion(fileId)
                : fileService.retryPublicOssPromotion(limit);
        return success(BeanUtils.toBean(result, FileOssPromoteRetryRespVO.class));
    }

    @GetMapping("/public-unpromoted")
    @Operation(summary = "列出已确认公开但未进入 OSS 的文件",
            description = "OSS 提升补传候选清单（运维可追踪）：内容仍停留在 master、需要被补传到公共桶的公开文件。")
    @PreAuthorize("@ss.hasPermission('infra:file:query')")
    public CommonResult<List<FileRespVO>> listPublicUnpromoted(
            @RequestParam(value = "limit", defaultValue = "200") Integer limit) {
        return success(BeanUtils.toBean(fileService.listPublicUnpromoted(limit), FileRespVO.class));
    }

}
