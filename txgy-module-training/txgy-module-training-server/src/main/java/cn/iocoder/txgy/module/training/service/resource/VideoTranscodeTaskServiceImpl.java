package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.training.config.VideoTranscodeProperties;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.TranscodeCapabilityRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoDefinitionRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoTranscodeTaskRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoResourceDO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoTranscodeTaskDO;
import cn.iocoder.txgy.module.training.dal.mysql.resource.VideoResourceMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.VideoTranscodeTaskMapper;
import cn.iocoder.txgy.module.training.enums.resource.ResourceConstants;
import cn.iocoder.txgy.module.training.enums.resource.TranscodeStatusEnum;
import cn.iocoder.txgy.module.training.enums.resource.TranscodeTaskStatusEnum;
import cn.iocoder.txgy.module.training.framework.video.FfmpegTranscodeExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 视频转码任务 Service 实现
 *
 * <p>上传成功后按目标清晰度（默认 360P / 480P / 720P）生成任务，由后台线程池异步执行
 * ffmpeg 转码，完成后回写视频的可播放清晰度列表。</p>
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Service
public class VideoTranscodeTaskServiceImpl implements VideoTranscodeTaskService {

    @Resource
    private VideoTranscodeTaskMapper taskMapper;
    @Resource
    private VideoResourceMapper videoMapper;
    @Resource
    private ResourceFileStorageService fileStorage;
    @Resource
    private VideoTranscodeProperties properties;
    @Resource
    private FfmpegTranscodeExecutor ffmpegExecutor;
    @Resource
    private OperationLogService operationLogService;
    @Resource
    @Qualifier("videoAsyncExecutor")
    private Executor asyncExecutor;

    @Override
    public List<VideoTranscodeTaskRespVO> getTaskList(Long videoId) {
        List<VideoTranscodeTaskDO> tasks = getTaskDOList(videoId);
        List<VideoTranscodeTaskRespVO> list = new ArrayList<>(tasks.size());
        for (VideoTranscodeTaskDO task : tasks) {
            VideoTranscodeTaskRespVO vo = BeanUtils.toBean(task, VideoTranscodeTaskRespVO.class);
            TranscodeTaskStatusEnum status = TranscodeTaskStatusEnum.fromStatus(task.getStatus());
            vo.setStatusName(status == null ? String.valueOf(task.getStatus()) : status.getName());
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<VideoTranscodeTaskDO> getTaskDOList(Long videoId) {
        return taskMapper.selectListByVideoId(videoId);
    }

    @Override
    public int createTasks(Long videoId, String sourceDefinition, String sourcePath) {
        List<String> targets = resolveTargets(sourceDefinition);
        if (targets.isEmpty()) {
            log.info("[createTasks][视频 {} 源清晰度 {} 无需生成转码任务]", videoId, sourceDefinition);
            return 0;
        }
        int count = 0;
        for (String definition : targets) {
            VideoTranscodeTaskDO task = new VideoTranscodeTaskDO();
            task.setVideoId(videoId);
            task.setDefinition(definition);
            task.setHeight(ResourceConstants.DEFINITION_HEIGHT.getOrDefault(definition, 720));
            task.setStatus(TranscodeTaskStatusEnum.PENDING.getStatus());
            task.setProgress(0);
            task.setSourcePath(sourcePath);
            task.setRetryCount(0);
            taskMapper.insert(task);
            count++;
        }
        return count;
    }

    /**
     * 目标清晰度：配置列表 ∩ 合法清晰度，且低于源清晰度（不做升码）
     */
    private List<String> resolveTargets(String sourceDefinition) {
        List<String> targets = new ArrayList<>();
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return targets;
        }
        Integer sourceHeight = ResourceConstants.DEFINITION_HEIGHT.get(
                StrUtil.blankToDefault(sourceDefinition, ResourceConstants.DEFINITION_720P).toUpperCase());
        if (sourceHeight == null) {
            sourceHeight = 720;
        }
        List<String> configured = properties.getTargetDefinitions();
        if (configured == null || configured.isEmpty()) {
            configured = ResourceConstants.DEFAULT_TRANSCODE_DEFINITIONS;
        }
        for (String definition : configured) {
            if (StrUtil.isBlank(definition)) {
                continue;
            }
            String upper = definition.trim().toUpperCase();
            if (!ResourceConstants.DEFINITIONS.contains(upper)) {
                continue;
            }
            Integer height = ResourceConstants.DEFINITION_HEIGHT.get(upper);
            if (height == null || height >= sourceHeight) {
                continue;
            }
            if (!targets.contains(upper)) {
                targets.add(upper);
            }
        }
        // 由高到低执行，便于优先产出常用档位
        targets.sort(Comparator.comparingInt(item -> ResourceConstants.DEFINITION_ORDER.indexOf(item)));
        return targets;
    }

    @Override
    public void submitTranscode(Long videoId, String tempPath, Long operatorId, String operatorName) {
        Long tenantId = TenantContextHolder.getTenantId();
        asyncExecutor.execute(() -> {
            TenantContextHolder.setTenantId(tenantId);
            try {
                process(videoId, tempPath, operatorId, operatorName);
            } catch (Exception e) {
                log.error("[submitTranscode][视频 {} 异步处理异常]", videoId, e);
            } finally {
                TenantContextHolder.clear();
            }
        });
    }

    /**
     * 异步主流程：临时文件归位 → 逐档转码 → 回写清晰度列表
     */
    private void process(Long videoId, String tempPath, Long operatorId, String operatorName) {
        VideoResourceDO video = videoMapper.selectById(videoId);
        if (video == null) {
            log.warn("[process][视频 {} 不存在，跳过异步处理]", videoId);
            return;
        }
        // 1. 临时文件归位到正式目录
        if (StrUtil.isNotBlank(tempPath)) {
            String finalPath = fileStorage.moveTo(tempPath,
                    fileStorage.buildRelPath("video", StrUtil.blankToDefault(video.getFileType(), "mp4")));
            VideoResourceDO update = new VideoResourceDO();
            update.setId(videoId);
            update.setFilePath(finalPath);
            videoMapper.updateById(update);
            video.setFilePath(finalPath);
        }
        // 2. 转码能力探测：不可用时任务标记失败，视频保持可播放（原始清晰度）
        if (!Boolean.TRUE.equals(properties.getEnabled()) || !ffmpegExecutor.isAvailable()) {
            markAllSkipped(videoId, "转码未开启或运行环境缺少 ffmpeg");
            return;
        }
        // 3. 逐档转码
        List<VideoTranscodeTaskDO> tasks = taskMapper.selectListByVideoId(videoId);
        if (tasks.isEmpty()) {
            refreshVideo(videoId, operatorId, operatorName);
            return;
        }
        File source = fileStorage.getFile(video.getFilePath());
        if (!source.exists()) {
            markAllSkipped(videoId, "源视频文件不存在：" + video.getFilePath());
            return;
        }
        for (VideoTranscodeTaskDO task : tasks) {
            if (TranscodeTaskStatusEnum.SUCCESS.getStatus().equals(task.getStatus())) {
                continue;
            }
            runTask(video, task);
        }
        // 4. 回写清晰度列表与转码状态
        refreshVideo(videoId, operatorId, operatorName);
    }

    /**
     * 执行单个转码任务（失败自动重试 {@code maxRetry} 次）
     */
    private void runTask(VideoResourceDO video, VideoTranscodeTaskDO task) {
        int maxRetry = properties.getMaxRetry() == null ? 0 : Math.max(0, properties.getMaxRetry());
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            updateTaskStatus(task.getId(), TranscodeTaskStatusEnum.PROCESSING.getStatus(),
                    attempt == 0 ? 0 : task.getProgress(), null, null, null, attempt);
            File target = fileStorage.getFile(buildOutputPath(video.getId(), task.getDefinition()));
            if (target.getParentFile() != null) {
                target.getParentFile().mkdirs();
            }
            AtomicInteger lastPersist = new AtomicInteger(-10);
            long totalSeconds = video.getDurationSeconds() == null ? 0 : Math.max(0, video.getDurationSeconds());
            FfmpegTranscodeExecutor.TranscodeResult result = ffmpegExecutor.transcode(
                    fileStorage.getFile(video.getFilePath()), target, task.getDefinition(),
                    task.getHeight() == null ? 720 : task.getHeight(), totalSeconds, percent -> {
                        if (percent - lastPersist.get() >= 5) {
                            lastPersist.set(percent);
                            updateProgress(task.getId(), percent);
                        }
                    });
            if (result.isSuccess()) {
                updateTaskStatus(task.getId(), TranscodeTaskStatusEnum.SUCCESS.getStatus(), 100,
                        buildOutputPath(video.getId(), task.getDefinition()), result.getFileSize(),
                        result.getCommand(), attempt);
                task.setStatus(TranscodeTaskStatusEnum.SUCCESS.getStatus());
                return;
            }
            if (attempt >= maxRetry) {
                updateTaskStatus(task.getId(), TranscodeTaskStatusEnum.FAILED.getStatus(),
                        task.getProgress(), null, null, result.getCommand(), attempt);
                updateTaskError(task.getId(), result.getErrorMsg());
                task.setStatus(TranscodeTaskStatusEnum.FAILED.getStatus());
            } else {
                log.warn("[runTask][视频 {} 清晰度 {} 第 {} 次转码失败，准备重试：{}]",
                        video.getId(), task.getDefinition(), attempt + 1, result.getErrorMsg());
            }
        }
    }

    private void updateProgress(Long taskId, int percent) {
        VideoTranscodeTaskDO update = new VideoTranscodeTaskDO();
        update.setId(taskId);
        update.setProgress(percent);
        taskMapper.updateById(update);
    }

    private void updateTaskStatus(Long taskId, Integer status, Integer progress, String outputPath,
                                  Long fileSize, String command, Integer retryCount) {
        VideoTranscodeTaskDO update = new VideoTranscodeTaskDO();
        update.setId(taskId);
        update.setStatus(status);
        update.setProgress(progress);
        update.setOutputPath(outputPath);
        update.setFileSize(fileSize);
        update.setCommand(command);
        update.setRetryCount(retryCount);
        LocalDateTime now = LocalDateTime.now();
        if (TranscodeTaskStatusEnum.PROCESSING.getStatus().equals(status)) {
            update.setStartTime(now);
        } else if (TranscodeTaskStatusEnum.isFinished(status)) {
            update.setEndTime(now);
        }
        taskMapper.updateById(update);
    }

    private void updateTaskError(Long taskId, String errorMsg) {
        VideoTranscodeTaskDO update = new VideoTranscodeTaskDO();
        update.setId(taskId);
        String msg = StrUtil.blankToDefault(errorMsg, "转码失败");
        update.setErrorMsg(msg.length() > 500 ? msg.substring(0, 500) : msg);
        update.setEndTime(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void markAllSkipped(Long videoId, String reason) {
        List<VideoTranscodeTaskDO> tasks = taskMapper.selectListByVideoId(videoId);
        for (VideoTranscodeTaskDO task : tasks) {
            if (TranscodeTaskStatusEnum.SUCCESS.getStatus().equals(task.getStatus())) {
                continue;
            }
            updateTaskStatus(task.getId(), TranscodeTaskStatusEnum.FAILED.getStatus(),
                    0, null, null, null, task.getRetryCount());
            updateTaskError(task.getId(), reason);
        }
        VideoResourceDO video = videoMapper.selectById(videoId);
        if (video == null) {
            return;
        }
        VideoResourceDO update = new VideoResourceDO();
        update.setId(videoId);
        update.setTranscodeStatus(tasks.isEmpty()
                ? TranscodeStatusEnum.NONE.getStatus() : TranscodeStatusEnum.FAILED.getStatus());
        update.setDefinitionOptions(JSONUtil.toJsonStr(buildOptions(video, tasks)));
        videoMapper.updateById(update);
        log.warn("[markAllSkipped][视频 {} 转码未执行：{}]", videoId, reason);
    }

    /**
     * 汇总转码结果，回写视频清晰度列表与转码状态
     */
    private void refreshVideo(Long videoId, Long operatorId, String operatorName) {
        VideoResourceDO video = videoMapper.selectById(videoId);
        if (video == null) {
            return;
        }
        List<VideoTranscodeTaskDO> tasks = taskMapper.selectListByVideoId(videoId);
        long success = tasks.stream()
                .filter(t -> TranscodeTaskStatusEnum.SUCCESS.getStatus().equals(t.getStatus()))
                .count();
        Integer transcodeStatus;
        if (tasks.isEmpty()) {
            transcodeStatus = TranscodeStatusEnum.NONE.getStatus();
        } else if (success > 0) {
            transcodeStatus = TranscodeStatusEnum.SUCCESS.getStatus();
        } else {
            transcodeStatus = TranscodeStatusEnum.FAILED.getStatus();
        }
        VideoResourceDO update = new VideoResourceDO();
        update.setId(videoId);
        update.setTranscodeStatus(transcodeStatus);
        update.setDefinitionOptions(JSONUtil.toJsonStr(buildOptions(video, tasks)));
        videoMapper.updateById(update);
        operationLogService.createLog(ResourceConstants.ACTION_TRANSCODE, ResourceConstants.TARGET_VIDEO,
                videoId, "视频《" + video.getTitle() + "》转码完成：" + success + "/" + tasks.size()
                        + " 档成功" + (success > 0 ? "（可切换 " + String.join("/", successDefinitions(tasks)) + "）" : ""));
    }

    private List<String> successDefinitions(List<VideoTranscodeTaskDO> tasks) {
        List<String> list = new ArrayList<>();
        for (VideoTranscodeTaskDO task : tasks) {
            if (TranscodeTaskStatusEnum.SUCCESS.getStatus().equals(task.getStatus())) {
                list.add(task.getDefinition());
            }
        }
        return list;
    }

    /**
     * 构建可播放清晰度列表：原始清晰度 + 已转码成功的档位（按清晰度由高到低）
     */
    private List<VideoDefinitionRespVO> buildOptions(VideoResourceDO video, List<VideoTranscodeTaskDO> tasks) {
        List<VideoDefinitionRespVO> options = new ArrayList<>();
        String source = StrUtil.blankToDefault(video.getDefinition(), ResourceConstants.DEFINITION_720P);
        options.add(new VideoDefinitionRespVO(source, playUrl(video.getId(), null), true, true));
        for (VideoTranscodeTaskDO task : tasks) {
            if (!TranscodeTaskStatusEnum.SUCCESS.getStatus().equals(task.getStatus())) {
                continue;
            }
            options.add(new VideoDefinitionRespVO(task.getDefinition(),
                    playUrl(video.getId(), task.getDefinition()), true, false));
        }
        options.sort(Comparator.comparingInt(item -> {
            int idx = ResourceConstants.DEFINITION_ORDER.indexOf(item.getDefinition());
            return idx < 0 ? 99 : idx;
        }));
        return options;
    }

    private String playUrl(Long videoId, String definition) {
        String url = "/admin-api/training/video/play?id=" + videoId;
        return StrUtil.isBlank(definition) ? url : url + "&definition=" + definition;
    }

    private String buildOutputPath(Long videoId, String definition) {
        String dir = StrUtil.blankToDefault(properties.getOutputDir(), "video/transcode");
        return dir + "/" + videoId + "_" + definition.toLowerCase() + ".mp4";
    }

    @Override
    public void retryTranscode(Long videoId) {
        VideoResourceDO video = videoMapper.selectById(videoId);
        if (video == null) {
            return;
        }
        List<VideoTranscodeTaskDO> tasks = taskMapper.selectListByVideoId(videoId);
        if (tasks.isEmpty()) {
            // 无任务（如转码能力缺失时上传）则按当前源清晰度重建
            createTasks(videoId, video.getDefinition(), video.getFilePath());
        } else {
            for (VideoTranscodeTaskDO task : tasks) {
                if (TranscodeTaskStatusEnum.SUCCESS.getStatus().equals(task.getStatus())) {
                    continue;
                }
                VideoTranscodeTaskDO update = new VideoTranscodeTaskDO();
                update.setId(task.getId());
                update.setStatus(TranscodeTaskStatusEnum.PENDING.getStatus());
                update.setProgress(0);
                update.setErrorMsg(null);
                update.setRetryCount(0);
                update.setEndTime(null);
                taskMapper.updateById(update);
            }
        }
        VideoResourceDO update = new VideoResourceDO();
        update.setId(videoId);
        update.setTranscodeStatus(TranscodeStatusEnum.PENDING.getStatus());
        videoMapper.updateById(update);
        submitTranscode(videoId, null, null, null);
    }

    @Override
    public TranscodeCapabilityRespVO getCapability() {
        boolean available = ffmpegExecutor.isAvailable();
        return TranscodeCapabilityRespVO.builder()
                .enabled(Boolean.TRUE.equals(properties.getEnabled()))
                .ffmpegAvailable(available)
                .ffmpegVersion(available ? ffmpegExecutor.version() : "")
                .targetDefinitions(resolveTargets(ResourceConstants.DEFINITION_2160P))
                .poolSize(properties.getPoolSize())
                .build();
    }

    @Override
    public String resolvePlayPath(Long videoId, String definition) {
        if (StrUtil.isBlank(definition)) {
            return null;
        }
        VideoTranscodeTaskDO task = taskMapper.selectSuccessByVideoAndDefinition(videoId, definition.trim().toUpperCase());
        return task == null ? null : task.getOutputPath();
    }

    @Override
    public void deleteTasksByVideoId(Long videoId) {
        List<VideoTranscodeTaskDO> tasks = taskMapper.selectListByVideoId(videoId);
        for (VideoTranscodeTaskDO task : tasks) {
            if (StrUtil.isNotBlank(task.getOutputPath())) {
                fileStorage.deleteFile(task.getOutputPath());
            }
            taskMapper.deleteById(task.getId());
        }
    }

}
