package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.io.FileUtil;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoResourceDO;
import cn.iocoder.txgy.module.training.dal.mysql.resource.VideoResourceMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 云培训学院 - 视频资源库种子数据初始化器
 *
 * <p>应用启动时自动为所有租户注入 64 个标准培训视频（24 个课程视频 + 40 个操作演示）。
 * 视频文件位于 ${txgy.resource.upload-dir}/resource/video/seed/ 目录，
 * 元数据按租户隔离插入 training_video_resource 表，幂等执行。</p>
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Component
public class TrainingVideoSeedDataInitializer implements ApplicationRunner {

    /** 种子视频文件路径前缀（用于幂等判断） */
    private static final String SEED_PATH_PREFIX = "video/seed/";

    /** 种子视频相对目录（相对于 resource 根目录） */
    private static final String SEED_REL_DIR = "video/seed";

    @Resource
    private VideoResourceMapper videoResourceMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private ResourceFileStorageService fileStorage;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("[种子数据][视频资源库] 开始初始化...");

            // 1. 读取种子视频文件列表
            File seedDir = new File(fileStorage.getRootDir(), SEED_REL_DIR);
            if (!seedDir.exists() || !seedDir.isDirectory()) {
                log.warn("[种子数据][视频资源库] 种子目录不存在: {}，跳过初始化", seedDir.getAbsolutePath());
                return;
            }
            File[] videoFiles = seedDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".mp4") || name.toLowerCase().endsWith(".mov"));
            if (videoFiles == null || videoFiles.length == 0) {
                log.warn("[种子数据][视频资源库] 种子目录无视频文件，跳过初始化");
                return;
            }
            log.info("[种子数据][视频资源库] 发现 {} 个种子视频文件", videoFiles.length);

            // 2. 查询所有租户
            List<Long> tenantIds = jdbcTemplate.queryForList(
                    "SELECT id FROM system_tenant WHERE deleted = 0", Long.class);
            log.info("[种子数据][视频资源库] 系统共有 {} 个租户", tenantIds.size());

            // 3. 为每个租户幂等插入种子视频
            int totalInserted = 0;
            for (Long tenantId : tenantIds) {
                try {
                    TenantContextHolder.setTenantId(tenantId);
                    int inserted = initTenantVideos(tenantId, videoFiles);
                    totalInserted += inserted;
                    if (inserted > 0) {
                        log.info("[种子数据][视频资源库] 租户 {} 插入 {} 条种子视频", tenantId, inserted);
                    }
                } finally {
                    TenantContextHolder.clear();
                }
            }

            log.info("[种子数据][视频资源库] 初始化完成，共插入 {} 条记录", totalInserted);
        } catch (Exception e) {
            log.error("[种子数据][视频资源库] 初始化失败", e);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * 为单个租户初始化种子视频（幂等）
     */
    private int initTenantVideos(Long tenantId, File[] videoFiles) {
        // 幂等判断：检查该租户是否已有种子视频（file_path 以 video/seed/ 开头）
        Long existingCount = videoResourceMapper.selectCount(
                new LambdaQueryWrapperX<VideoResourceDO>()
                        .likeRight(VideoResourceDO::getFilePath, SEED_PATH_PREFIX));
        if (existingCount != null && existingCount > 0) {
            return 0; // 已初始化过，跳过
        }

        int inserted = 0;
        for (File videoFile : videoFiles) {
            String fileName = videoFile.getName();
            String title = extractTitle(fileName);
            String filePath = SEED_REL_DIR + "/" + fileName;
            String fileType = FileUtil.extName(fileName).toLowerCase();
            long fileSize = videoFile.length();

            VideoResourceDO video = new VideoResourceDO();
            video.setTitle(title);
            video.setFileName(fileName);
            video.setFilePath(filePath);
            video.setFileType(fileType);
            video.setFileSize(fileSize);
            video.setDurationSeconds(30); // 测试视频默认 30 秒
            video.setDefinition("720P");
            video.setVideoType(2); // 2 录播
            video.setStatus(2); // 2 上线
            video.setTranscodeStatus(3); // 3 转码成功
            video.setUploaderId(1L);
            video.setUploaderName("系统种子");
            video.setViewCount(0);
            // 先插入获取自增 ID，再用 ID 构建播放地址
            videoResourceMapper.insert(video);
            Long videoId = video.getId();
            if (videoId != null) {
                video.setDefinitionOptions(buildDefinitionOptions(videoId));
                videoResourceMapper.updateById(video);
            }
            inserted++;
        }
        return inserted;
    }

    /**
     * 从文件名提取标题
     * 例如: "V01_喷砂除锈标准作业演示.mp4" -> "喷砂除锈标准作业演示"
     *       "C01_公司业务全景与产品线认知_第1讲.mp4" -> "公司业务全景与产品线认知 第1讲"
     */
    private String extractTitle(String fileName) {
        String name = fileName.substring(0, fileName.lastIndexOf('.'));
        // 去掉前缀编号（V01_ / C01_ 等）
        if (name.length() > 3 && name.charAt(2) == '_') {
            name = name.substring(3);
        }
        // 下划线替换为空格
        name = name.replace('_', ' ');
        return name.trim();
    }

    /**
     * 构建多清晰度选项 JSON（用视频 ID 构建播放地址）
     */
    private String buildDefinitionOptions(Long videoId) {
        String playUrl = "/admin-api/training/video/play?id=" + videoId;
        return "["
                + "{\"definition\":\"720P\",\"url\":\"" + playUrl + "\",\"transcoded\":true,\"original\":true},"
                + "{\"definition\":\"480P\",\"url\":\"" + playUrl + "\",\"transcoded\":false,\"original\":false},"
                + "{\"definition\":\"360P\",\"url\":\"" + playUrl + "\",\"transcoded\":false,\"original\":false}"
                + "]";
    }
}
