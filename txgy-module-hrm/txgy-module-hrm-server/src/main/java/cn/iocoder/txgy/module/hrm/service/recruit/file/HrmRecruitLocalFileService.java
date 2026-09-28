package cn.iocoder.txgy.module.hrm.service.recruit.file;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** HRM 招聘门户文件的本地存储，避免简历与沟通附件依赖公共文件服务。 */
@Service
public class HrmRecruitLocalFileService {

    private static final Set<String> CATEGORIES = Set.of("resume", "resume-avatar", "avatar", "chat");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> RESUME_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final long MAX_RESUME_SIZE = 10L * 1024 * 1024;
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final Pattern STORED_FILE_PATTERN = Pattern.compile(
            "(?:resume|resume-avatar|avatar|chat)-[0-9]+-[0-9a-f]{32}\\.(?:pdf|doc|docx|jpg|jpeg|png|gif|webp)");
    private static final String FILE_URL_PREFIX = "/admin-api/hrm/portal/recruit/file/";

    private final Path root;

    public HrmRecruitLocalFileService(
            @Value("${hrm.recruit.local-file-dir:${user.home}/uploads/hrm/recruit/files}") String fileDir) {
        this.root = Paths.get(fileDir).toAbsolutePath().normalize();
    }

    public String save(MultipartFile file, String category) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("文件目录不正确");
        }
        String originalName = StringUtils.getFilename(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!isAllowedExtension(category, extension)) {
            throw new IllegalArgumentException("简历仅支持 PDF、DOC、DOCX，图片仅支持 JPG、PNG、GIF、WEBP");
        }
        long maxSize = "resume".equals(category) ? MAX_RESUME_SIZE : MAX_IMAGE_SIZE;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("文件不能超过 10MB");
        }

        Files.createDirectories(root);
        Long tenantId = TenantContextHolder.getTenantId();
        String storedName = category + "-" + (tenantId == null ? 0 : tenantId)
                + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("文件路径不正确");
        }
        file.transferTo(target);
        return FILE_URL_PREFIX + storedName;
    }

    public Resource load(String fileName) {
        if (fileName == null || !STORED_FILE_PATTERN.matcher(fileName).matches()) {
            return null;
        }
        Path target = root.resolve(fileName).normalize();
        return target.startsWith(root) && Files.isRegularFile(target)
                ? new FileSystemResource(target)
                : null;
    }

    public static boolean isLocalFileUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return false;
        }
        try {
            java.net.URI uri = java.net.URI.create(fileUrl);
            return uri.getPath() != null && uri.getPath().startsWith(FILE_URL_PREFIX);
        } catch (IllegalArgumentException ignored) {
            return fileUrl.startsWith(FILE_URL_PREFIX);
        }
    }

    private static boolean isAllowedExtension(String category, String extension) {
        if (extension == null || extension.isBlank()) {
            return false;
        }
        return "resume".equals(category)
                ? RESUME_EXTENSIONS.contains(extension)
                : IMAGE_EXTENSIONS.contains(extension);
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
