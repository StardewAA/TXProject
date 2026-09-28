package cn.iocoder.txgy.module.infra.controller.app.file;

import cn.hutool.core.io.IoUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.txgy.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.txgy.module.infra.controller.app.file.vo.AppFileUploadReqVO;
import cn.iocoder.txgy.module.infra.service.file.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - 文件存储")
@RestController
@RequestMapping("/infra/file")
@Validated
@Slf4j
public class AppFileController {

    @Resource
    private FileService fileService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "必须携带有效会员或后台登录态；tenant-id 请求头不能替代登录身份")
    @Parameter(name = "file", description = "文件附件", required = true,
            schema = @Schema(type = "string", format = "binary"))
    public CommonResult<String> uploadFile(@Valid AppFileUploadReqVO uploadReqVO) throws Exception {
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

    /**
     * 用户端（会员）文件读取通道
     *
     * 背景：后台读取通道位于 /admin-api，网关/框架会强制校验 userType=ADMIN，
     * 会员 token 在 /admin-api 上会被拒绝（错误的用户类型）。因此会员端必须走 /app-api，
     * 与本端点的 URL 前缀一致（userType=MEMBER），再由服务端按文件归属/参与方做鉴权。
     *
     * 安全：公开资源匿名可读；私有资源需登录且通过归属校验，无法确认归属时拒绝。
     */
    @GetMapping("/{configId}/get/**")
    @TenantIgnore
    @Operation(summary = "下载文件（用户端）",
            description = "公开资源匿名可读；私有资源需登录且通过归属/参与方校验（无法确认归属时拒绝）")
    @Parameter(name = "configId", description = "配置编号", required = true)
    public void getFileContent(HttpServletRequest request,
                               HttpServletResponse response,
                               @PathVariable("configId") Long configId) throws Exception {
        fileService.handleReadRequest(request, response, configId, false);
    }

    @GetMapping("/{configId}/preview/**")
    @TenantIgnore
    @Operation(summary = "在线预览文件（用户端）",
            description = "公开资源匿名可读；私有资源需登录且通过归属/参与方校验（无法确认归属时拒绝）")
    @Parameter(name = "configId", description = "配置编号", required = true)
    public void previewFileContent(HttpServletRequest request,
                                   HttpServletResponse response,
                                   @PathVariable("configId") Long configId) throws Exception {
        fileService.handleReadRequest(request, response, configId, true);
    }

}
