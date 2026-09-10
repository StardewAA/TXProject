package cn.iocoder.txgy.module.training.controller.admin.resource;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoDefinitionRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoResourceDO;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.service.resource.ResourceFileStorageService;
import cn.iocoder.txgy.module.training.service.resource.VideoResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 视频资源库")
@RestController
@RequestMapping("/training/video")
@Validated
public class VideoResourceController {

    @Resource
    private VideoResourceService videoService;
    @Resource
    private ResourceFileStorageService fileStorage;

    @PostMapping("/upload")
    @Operation(summary = "上传视频（短视频/录播，MP4/MOV ≤2GB，成功即上线，记录原始清晰度）")
    @PreAuthorize("@ss.hasPermission('training:resource-video:create')")
    public CommonResult<Long> uploadVideo(@RequestParam("file") MultipartFile file,
                                          @RequestParam("title") String title,
                                          @RequestParam(value = "categoryId", required = false) Long categoryId,
                                          @RequestParam(value = "coverUrl", required = false) String coverUrl,
                                          @RequestParam(value = "tags", required = false) String tags,
                                          @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
                                          @RequestParam(value = "videoType", required = false) Integer videoType,
                                          @RequestParam(value = "definition", required = false) String definition) {
        return success(videoService.uploadVideo(file, title, categoryId, coverUrl, tags,
                durationSeconds, videoType, definition));
    }

    @GetMapping("/page")
    @Operation(summary = "视频分页列表")
    @PreAuthorize("@ss.hasPermission('training:resource-video:query')")
    public CommonResult<PageResult<VideoResourceDO>> getVideoPage(@Validated VideoPageReqVO pageReqVO) {
        return success(videoService.getVideoPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "视频详情")
    @Parameter(name = "id", description = "视频资源 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:resource-video:query')")
    public CommonResult<VideoResourceDO> getVideo(@RequestParam("id") Long id) {
        return success(videoService.getVideo(id));
    }

    @PutMapping("/update")
    @Operation(summary = "更新视频元数据")
    @PreAuthorize("@ss.hasPermission('training:resource-video:update')")
    public CommonResult<Boolean> updateVideo(@RequestParam("id") Long id,
                                             @RequestParam("title") String title,
                                             @RequestParam(value = "categoryId", required = false) Long categoryId,
                                             @RequestParam(value = "coverUrl", required = false) String coverUrl,
                                             @RequestParam(value = "tags", required = false) String tags,
                                             @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
                                             @RequestParam(value = "videoType", required = false) Integer videoType,
                                             @RequestParam(value = "definition", required = false) String definition) {
        videoService.updateVideo(id, title, categoryId, coverUrl, tags, durationSeconds, videoType, definition);
        return success(true);
    }

    @GetMapping("/definitions")
    @Operation(summary = "查询可播放清晰度列表（原始清晰度 + 更低档位，弱网可向下切换）")
    @Parameter(name = "id", description = "视频资源 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:resource-video:query')")
    public CommonResult<List<VideoDefinitionRespVO>> getDefinitionOptions(@RequestParam("id") Long id) {
        return success(videoService.getDefinitionOptions(id));
    }

    @PutMapping("/definitions")
    @Operation(summary = "保存转码后的多码率清晰度地址（转码完成后写入，供弱网切换）")
    @PreAuthorize("@ss.hasPermission('training:resource-video:update')")
    public CommonResult<Boolean> saveDefinitionOptions(
            @RequestBody List<VideoDefinitionRespVO> options,
            @RequestParam("id") Long id) {
        videoService.saveDefinitionOptions(id, options);
        return success(true);
    }

    @PostMapping("/status")
    @Operation(summary = "视频状态流转：DRAFT草稿/ONLINE上线/OFFLINE下架/ARCHIVED归档")
    @PreAuthorize("@ss.hasPermission('training:resource-video:update')")
    public CommonResult<Boolean> changeVideoStatus(@RequestParam("id") Long id,
                                                   @RequestParam("action") String action) {
        videoService.changeVideoStatus(id, action);
        return success(true);
    }

    @PostMapping("/transcode")
    @Operation(summary = "标记转码成功（易版占位）")
    @PreAuthorize("@ss.hasPermission('training:resource-video:update')")
    public CommonResult<Boolean> markTranscodeSuccess(@RequestParam("id") Long id) {
        videoService.markTranscodeSuccess(id);
        return success(true);
    }

    @PostMapping("/credit-hours")
    @Operation(summary = "独立配置视频学时值（>0，如 0.5/1/1.5）")
    @PreAuthorize("@ss.hasPermission('training:resource-video:update')")
    public CommonResult<Boolean> setCreditHours(@RequestParam("id") Long id,
                                                @RequestParam("creditHours") BigDecimal creditHours) {
        videoService.setCreditHours(id, creditHours);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除视频（被引用时禁止删除）")
    @Parameter(name = "id", description = "视频资源 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:resource-video:delete')")
    public CommonResult<Boolean> deleteVideo(@RequestParam("id") Long id) {
        videoService.deleteVideo(id);
        return success(true);
    }

    // ========== 视频播放（支持 HTTP Range，匿名可访问） ==========

    @GetMapping("/play")
    @Operation(summary = "视频播放流（支持 Range 拖动播放）")
    @PermitAll
    @TenantIgnore
    public void playVideo(@RequestParam("id") Long id,
                          HttpServletRequest request, HttpServletResponse response) throws Exception {
        VideoResourceDO video = videoService.getVideo(id);
        File file = fileStorage.loadFile(video.getFilePath());
        if (file == null || !file.exists()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_FILE_NOT_FOUND);
        }
        long length = file.length();
        response.setContentType(ResourceFileStorageService.contentTypeOf(video.getFileType()));
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "public, max-age=3600");

        long start = 0;
        long end = length - 1;
        boolean partial = false;
        String rangeHeader = request.getHeader("Range");
        if (StrUtil.isNotBlank(rangeHeader) && rangeHeader.startsWith("bytes=")) {
            String[] parts = rangeHeader.substring("bytes=".length()).split("-", 2);
            try {
                start = Long.parseLong(parts[0].trim());
                if (parts.length > 1 && StrUtil.isNotBlank(parts[1])) {
                    end = Math.min(Long.parseLong(parts[1].trim()), length - 1);
                }
            } catch (NumberFormatException ignored) {
                // 非法 Range 头按全量输出
            }
            if (start > end || start >= length) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + length);
                return;
            }
            partial = true;
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
        }
        response.setContentLengthLong(partial ? end - start + 1 : length);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            byte[] buffer = new byte[64 * 1024];
            long remaining = end - start + 1;
            OutputStream out = response.getOutputStream();
            int read;
            while (remaining > 0 && (read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                out.write(buffer, 0, read);
                remaining -= read;
            }
            out.flush();
        }
    }

}
