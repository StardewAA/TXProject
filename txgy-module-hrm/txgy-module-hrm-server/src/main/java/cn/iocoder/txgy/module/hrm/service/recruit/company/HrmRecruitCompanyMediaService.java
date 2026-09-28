package cn.iocoder.txgy.module.hrm.service.recruit.company;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** HRM 招聘企业图片的本地存储。 */
@Service
public class HrmRecruitCompanyMediaService {

    private static final Set<String> CATEGORIES = Set.of("logo", "product", "album");
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Pattern STORED_FILE_PATTERN = Pattern.compile(
            "(?:logo|product|album)-[0-9]+-[0-9a-f]{32}\\.(?:jpg|jpeg|png|gif|webp)");

    private final Path root;

    public HrmRecruitCompanyMediaService(
            @Value("${hrm.recruit.company-media-dir:${user.home}/uploads/hrm/recruit/company}") String mediaDir) {
        this.root = Paths.get(mediaDir).toAbsolutePath().normalize();
    }

    public String save(MultipartFile file, String category) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片文件不能为空");
        }
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("图片目录不正确");
        }
        String originalName = StringUtils.getFilename(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("只支持 JPG、PNG、GIF、WEBP 图片");
        }

        Files.createDirectories(root);
        Long tenantId = TenantContextHolder.getTenantId();
        String storedName = category + "-" + (tenantId == null ? 0 : tenantId)
                + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("图片路径不正确");
        }
        file.transferTo(target);
        return "/admin-api/hrm/recruit/company/file/" + storedName;
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

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
