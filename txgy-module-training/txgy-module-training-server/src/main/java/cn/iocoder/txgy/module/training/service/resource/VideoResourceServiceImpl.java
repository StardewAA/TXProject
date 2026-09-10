package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoDefinitionRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoResourceDO;
import cn.iocoder.txgy.module.training.dal.mysql.resource.ResourceReferenceMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.VideoResourceMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.enums.resource.ResourceConstants;
import cn.iocoder.txgy.module.training.enums.resource.ResourceStatusEnum;
import cn.iocoder.txgy.module.training.enums.resource.TranscodeStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 视频资源 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Service
public class VideoResourceServiceImpl implements VideoResourceService {

    @Resource
    private VideoResourceMapper videoMapper;
    @Resource
    private ResourceReferenceMapper referenceMapper;
    @Resource
    private ResourceFileStorageService fileStorage;
    @Resource
    private ResourceCategoryService categoryService;
    @Resource
    private OperationLogService operationLogService;

    @Override
    public PageResult<VideoResourceDO> getVideoPage(VideoPageReqVO reqVO) {
        return videoMapper.selectPage(reqVO);
    }

    @Override
    public VideoResourceDO getVideo(Long id) {
        VideoResourceDO video = videoMapper.selectById(id);
        if (video == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.VIDEO_NOT_EXISTS);
        }
        return video;
    }

    @Override
    public Long uploadVideo(MultipartFile file, String title, Long categoryId, String coverUrl,
                            String tags, Integer durationSeconds, Integer videoType, String definition) {
        String ext = validateFile(file);
        categoryService.validateCategory(categoryId, ResourceConstants.TYPE_VIDEO);
        String filePath = fileStorage.store(file, "video", ext);
        String finalDefinition = normalizeDefinition(definition);
        String finalVideoType = videoTypeName(normalizeVideoType(videoType));
        VideoResourceDO video = new VideoResourceDO();
        video.setTitle(StrUtil.trim(title));
        video.setCategoryId(categoryId);
        video.setCoverUrl(coverUrl);
        video.setFileName(file.getOriginalFilename());
        video.setFilePath(filePath);
        video.setFileType(ext);
        video.setFileSize(file.getSize());
        video.setDurationSeconds(durationSeconds == null ? 0 : Math.max(0, durationSeconds));
        video.setVideoType(normalizeVideoType(videoType));
        video.setDefinition(finalDefinition);
        video.setTranscodeStatus(TranscodeStatusEnum.NONE.getStatus());
        video.setTags(tags);
        video.setStatus(ResourceStatusEnum.ONLINE.getStatus());
        video.setViewCount(0);
        video.setUploaderId(SecurityFrameworkUtils.getLoginUserId());
        video.setUploaderName(SecurityFrameworkUtils.getLoginUserNickname());
        videoMapper.insert(video);
        // 生成默认清晰度列表：原始清晰度 + 更低档位（未转码项暂复用原始播放地址）
        saveDefaultDefinitionOptions(video.getId(), finalDefinition);
        operationLogService.createLog(ResourceConstants.ACTION_UPLOAD, ResourceConstants.TARGET_VIDEO,
                video.getId(), "上传" + finalVideoType + "《" + title + "》，原始清晰度 " + finalDefinition
                        + "，大小 " + formatSize(file.getSize()));
        return video.getId();
    }

    @Override
    public void updateVideo(Long id, String title, Long categoryId, String coverUrl,
                            String tags, Integer durationSeconds, Integer videoType, String definition) {
        VideoResourceDO old = getVideo(id);
        categoryService.validateCategory(categoryId, ResourceConstants.TYPE_VIDEO);
        String finalDefinition = normalizeDefinition(definition);
        VideoResourceDO update = new VideoResourceDO();
        update.setId(id);
        update.setTitle(StrUtil.trim(title));
        update.setCategoryId(categoryId);
        update.setCoverUrl(coverUrl);
        update.setTags(tags);
        update.setDurationSeconds(durationSeconds == null ? old.getDurationSeconds() : Math.max(0, durationSeconds));
        update.setVideoType(normalizeVideoType(videoType));
        update.setDefinition(finalDefinition);
        videoMapper.updateById(update);
        // 原始清晰度变化时重建降级列表
        if (!Objects.equals(old.getDefinition(), finalDefinition)) {
            saveDefaultDefinitionOptions(id, finalDefinition);
        }
        operationLogService.createLog(ResourceConstants.ACTION_UPDATE, ResourceConstants.TARGET_VIDEO,
                id, "更新视频《" + old.getTitle() + "》元数据（类型：" + videoTypeName(normalizeVideoType(videoType))
                        + "，原始清晰度：" + finalDefinition + "）");
    }

    @Override
    public void changeVideoStatus(Long id, String action) {
        VideoResourceDO video = getVideo(id);
        int from = video.getStatus();
        ResourceStatusEnum to = checkStatusAction(from, action);
        VideoResourceDO update = new VideoResourceDO();
        update.setId(id);
        update.setStatus(to.getStatus());
        videoMapper.updateById(update);
        operationLogService.createLog(action, ResourceConstants.TARGET_VIDEO, id,
                "视频《" + video.getTitle() + "》状态流转：" + statusName(from) + " → " + statusName(to.getStatus()));
    }

    @Override
    public void markTranscodeSuccess(Long id) {
        VideoResourceDO video = getVideo(id);
        VideoResourceDO update = new VideoResourceDO();
        update.setId(id);
        update.setTranscodeStatus(TranscodeStatusEnum.SUCCESS.getStatus());
        videoMapper.updateById(update);
        operationLogService.createLog(ResourceConstants.ACTION_TRANSCODE, ResourceConstants.TARGET_VIDEO,
                id, "视频《" + video.getTitle() + "》标记转码成功");
    }

    @Override
    public void setCreditHours(Long id, BigDecimal creditHours) {
        VideoResourceDO video = getVideo(id);
        if (creditHours == null || creditHours.compareTo(BigDecimal.ZERO) <= 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.VIDEO_CREDIT_HOURS_INVALID);
        }
        VideoResourceDO update = new VideoResourceDO();
        update.setId(id);
        update.setCreditHours(creditHours.setScale(2));
        videoMapper.updateById(update);
        operationLogService.createLog(ResourceConstants.ACTION_CREDIT, ResourceConstants.TARGET_VIDEO,
                id, "视频《" + video.getTitle() + "》配置学时：" + creditHours + "h");
    }

    @Override
    public void deleteVideo(Long id) {
        VideoResourceDO video = getVideo(id);
        Long refCount = referenceMapper.selectCountByResource(ResourceConstants.TARGET_VIDEO, id);
        if (refCount != null && refCount > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.VIDEO_REFERENCED_DELETE_FORBIDDEN);
        }
        videoMapper.deleteById(id);
        operationLogService.createLog(ResourceConstants.ACTION_DELETE, ResourceConstants.TARGET_VIDEO,
                id, "删除视频《" + video.getTitle() + "》");
    }

    @Override
    public List<VideoDefinitionRespVO> getDefinitionOptions(Long id) {
        VideoResourceDO video = getVideo(id);
        if (StrUtil.isNotBlank(video.getDefinitionOptions())) {
            try {
                List<VideoDefinitionRespVO> list = JSONUtil.toList(
                        JSONUtil.parseArray(video.getDefinitionOptions()), VideoDefinitionRespVO.class);
                if (list != null && !list.isEmpty()) {
                    return list;
                }
            } catch (Exception e) {
                log.warn("[getDefinitionOptions][视频 {} 清晰度配置解析失败，回退默认列表]", id, e);
            }
        }
        return buildDefaultDefinitionOptions(id,
                StrUtil.blankToDefault(video.getDefinition(), ResourceConstants.DEFINITION_720P));
    }

    @Override
    public void saveDefinitionOptions(Long id, List<VideoDefinitionRespVO> options) {
        VideoResourceDO video = getVideo(id);
        if (options == null || options.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.VIDEO_DEFINITION_INVALID);
        }
        List<VideoDefinitionRespVO> valid = new ArrayList<>();
        for (VideoDefinitionRespVO item : options) {
            if (item == null || StrUtil.isBlank(item.getDefinition()) || StrUtil.isBlank(item.getUrl())) {
                continue;
            }
            item.setDefinition(normalizeDefinition(item.getDefinition()));
            item.setTranscoded(item.getTranscoded() != null && item.getTranscoded());
            item.setOriginal(item.getOriginal() != null && item.getOriginal());
            valid.add(item);
        }
        if (valid.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.VIDEO_DEFINITION_INVALID);
        }
        VideoResourceDO update = new VideoResourceDO();
        update.setId(id);
        update.setDefinitionOptions(JSONUtil.toJsonStr(valid));
        update.setTranscodeStatus(TranscodeStatusEnum.SUCCESS.getStatus());
        videoMapper.updateById(update);
        operationLogService.createLog(ResourceConstants.ACTION_TRANSCODE, ResourceConstants.TARGET_VIDEO,
                id, "更新视频《" + video.getTitle() + "》清晰度列表：" + valid.size() + " 档");
    }

    // ========== 私有方法 ==========

    private Integer normalizeVideoType(Integer videoType) {
        if (videoType == null) {
            return ResourceConstants.VIDEO_TYPE_RECORD;
        }
        if (!Objects.equals(videoType, ResourceConstants.VIDEO_TYPE_SHORT)
                && !Objects.equals(videoType, ResourceConstants.VIDEO_TYPE_RECORD)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.VIDEO_TYPE_INVALID);
        }
        return videoType;
    }

    private String normalizeDefinition(String definition) {
        if (StrUtil.isBlank(definition)) {
            return ResourceConstants.DEFINITION_720P;
        }
        String upper = definition.trim().toUpperCase();
        if (!ResourceConstants.DEFINITIONS.contains(upper)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.VIDEO_DEFINITION_INVALID);
        }
        return upper;
    }

    private String videoTypeName(Integer videoType) {
        return Objects.equals(videoType, ResourceConstants.VIDEO_TYPE_SHORT) ? "短视频" : "录播";
    }

    /**
     * 生成并保存默认清晰度列表：原始清晰度（已转码）+ 更低档位（未转码，暂复用原始播放地址）
     */
    private void saveDefaultDefinitionOptions(Long id, String definition) {
        VideoResourceDO update = new VideoResourceDO();
        update.setId(id);
        update.setDefinitionOptions(JSONUtil.toJsonStr(buildDefaultDefinitionOptions(id, definition)));
        videoMapper.updateById(update);
    }

    private List<VideoDefinitionRespVO> buildDefaultDefinitionOptions(Long id, String definition) {
        String playUrl = "/admin-api/training/video/play?id=" + id;
        List<VideoDefinitionRespVO> list = new ArrayList<>();
        list.add(new VideoDefinitionRespVO(definition, playUrl, true, true));
        int idx = ResourceConstants.DEFINITION_ORDER.indexOf(definition);
        if (idx < 0) {
            idx = ResourceConstants.DEFINITION_ORDER.indexOf(ResourceConstants.DEFINITION_720P);
        }
        for (int i = idx + 1; i < ResourceConstants.DEFINITION_ORDER.size(); i++) {
            list.add(new VideoDefinitionRespVO(ResourceConstants.DEFINITION_ORDER.get(i), playUrl, false, false));
        }
        return list;
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_FILE_EMPTY);
        }
        String ext = ResourceConstants.getFileExt(file.getOriginalFilename());
        if (!ResourceConstants.VIDEO_ALLOWED_EXTS.contains(ext)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.VIDEO_FILE_TYPE_INVALID);
        }
        if (file.getSize() > ResourceConstants.MAX_VIDEO_SIZE) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.VIDEO_FILE_SIZE_EXCEED);
        }
        return ext;
    }

    private ResourceStatusEnum checkStatusAction(int from, String action) {
        if (ResourceConstants.ACTION_ONLINE.equals(action)) {
            if (from == ResourceStatusEnum.DRAFT.getStatus()
                    || from == ResourceStatusEnum.OFFLINE.getStatus()) {
                return ResourceStatusEnum.ONLINE;
            }
        } else if (ResourceConstants.ACTION_OFFLINE.equals(action)) {
            if (from == ResourceStatusEnum.ONLINE.getStatus()) {
                return ResourceStatusEnum.OFFLINE;
            }
        } else if (ResourceConstants.ACTION_ARCHIVE.equals(action)) {
            if (from != ResourceStatusEnum.ARCHIVED.getStatus()
                    && from != ResourceStatusEnum.DRAFT.getStatus()) {
                return ResourceStatusEnum.ARCHIVED;
            }
        } else if (ResourceConstants.ACTION_DRAFT.equals(action)) {
            if (from == ResourceStatusEnum.ARCHIVED.getStatus()) {
                return ResourceStatusEnum.DRAFT;
            }
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.VIDEO_STATUS_CHANGE_INVALID);
    }

    private String statusName(int status) {
        ResourceStatusEnum e = ResourceStatusEnum.fromStatus(status);
        return e == null ? String.valueOf(status) : e.getName();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        }
        return String.format("%.1fMB", bytes / 1024.0 / 1024.0);
    }

}
