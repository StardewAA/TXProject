package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * 资源文件存储服务
 *
 * <p>资源中心自持本地文件（非 infra 附件体系）：文件落在 ${txgy.resource.upload-dir}/resource 目录，
 * 通过文档/视频预览、播放接口直出（inline / Range）。部署时目录需挂载宿主机卷以持久化。</p>
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Service
public class ResourceFileStorageService {

    /**
     * 默认上传根目录（相对进程工作目录，容器内 WORKDIR=/app）
     */
    private static final String DEFAULT_UPLOAD_DIR = "./uploads";

    /**
     * 上传根目录：{@code ${txgy.resource.upload-dir:./uploads}/resource}，构造时绝对化
     */
    private final File rootDir;

    public ResourceFileStorageService(@Value("${txgy.resource.upload-dir:./uploads}") String uploadDir) {
        String path = StrUtil.blankToDefault(uploadDir, DEFAULT_UPLOAD_DIR).trim();
        // 防御：配置误指向 fat-jar 类加载路径（形如 nested:/app/app.jar/!BOOT-INF/classes/!/uploads）
        // 时物理上无法写入，回退到进程工作目录下的默认路径
        if (isJarInternalPath(path)) {
            log.warn("[构造][resource.upload-dir 配置异常({})，回退到默认路径 {}]", uploadDir, DEFAULT_UPLOAD_DIR);
            path = DEFAULT_UPLOAD_DIR;
        }
        this.rootDir = FileUtil.mkdir(path + "/resource").getAbsoluteFile();
        log.info("[构造][资源文件存储根目录初始化完成，configured({})，实际根目录({})]",
                uploadDir, this.rootDir.getAbsolutePath());
    }

    /**
     * 判断路径是否指向 fat-jar / classpath 内部（无法作为普通文件目录写入）
     */
    private static boolean isJarInternalPath(String path) {
        return path.contains("nested:") || path.contains("!BOOT-INF") || path.contains("/!/") || path.startsWith("!/");
    }

    /**
     * 保存上传文件，返回相对存储路径（resource 目录之下）
     *
     * @param file   上传文件
     * @param subDir 子目录（doc/video）
     * @param ext    扩展名（已由调用方白名单校验）
     */
    public String store(MultipartFile file, String subDir, String ext) {
        String dateDir = DateUtil.format(new Date(), "yyyyMM");
        String relPath = subDir + "/" + dateDir + "/" + IdUtil.fastSimpleUUID() + "." + ext;
        File target = FileUtil.file(rootDir, relPath);
        try {
            FileUtil.mkParentDirs(target);
            file.transferTo(target);
        } catch (IOException e) {
            log.error("[store][保存资源文件失败, relPath({}), target({})]", relPath, target.getAbsolutePath(), e);
            throw new IORuntimeException(e);
        }
        return relPath;
    }

    /**
     * 暂存上传文件（异步流程：先落临时目录，后台线程再归位到正式目录），返回临时相对路径
     */
    public String storeTemp(MultipartFile file, String subDir, String ext) {
        String relPath = subDir + "/tmp/" + IdUtil.fastSimpleUUID() + "." + ext;
        File target = FileUtil.file(rootDir, relPath);
        try {
            FileUtil.mkParentDirs(target);
            file.transferTo(target);
        } catch (IOException e) {
            log.error("[storeTemp][暂存资源文件失败, relPath({}), target({})]", relPath, target.getAbsolutePath(), e);
            throw new IORuntimeException(e);
        }
        return relPath;
    }

    /**
     * 将临时文件移动到正式相对路径（同分区 rename，开销极小）
     *
     * @param tempRelPath   临时相对路径
     * @param targetRelPath 正式相对路径
     * @return 正式相对路径
     */
    public String moveTo(String tempRelPath, String targetRelPath) {
        File source = FileUtil.file(rootDir, tempRelPath);
        File target = FileUtil.file(rootDir, targetRelPath);
        FileUtil.mkParentDirs(target);
        FileUtil.move(source, target, true);
        return targetRelPath;
    }

    /**
     * 生成正式存储相对路径：{subDir}/{yyyyMM}/{uuid}.{ext}
     */
    public String buildRelPath(String subDir, String ext) {
        return subDir + "/" + DateUtil.format(new Date(), "yyyyMM") + "/" + IdUtil.fastSimpleUUID() + "." + ext;
    }

    /**
     * 取相对路径对应的物理文件（不校验存在性，供转码等场景使用）
     */
    public File getFile(String relPath) {
        return FileUtil.file(rootDir, relPath);
    }

    /**
     * 根据相对路径取文件；文件不存在返回 null
     */
    public File loadFile(String relPath) {
        if (StrUtil.isBlank(relPath)) {
            return null;
        }
        File file = FileUtil.file(rootDir, relPath);
        return file.exists() ? file : null;
    }

    /**
     * 逻辑删除资源时的物理文件回收（由资源删除流程决定是否调用）
     */
    public void deleteFile(String relPath) {
        if (StrUtil.isBlank(relPath)) {
            return;
        }
        boolean deleted = FileUtil.del(FileUtil.file(rootDir, relPath));
        if (!deleted) {
            log.warn("[deleteFile][物理删除资源文件失败, path({})]", relPath);
        }
    }

    /**
     * 扩展名对应的响应 Content-Type（用于预览/播放直出）
     */
    public static String contentTypeOf(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            default -> "application/octet-stream";
        };
    }

}
