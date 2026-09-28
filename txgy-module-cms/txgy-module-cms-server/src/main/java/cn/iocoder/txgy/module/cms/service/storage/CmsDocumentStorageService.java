package cn.iocoder.txgy.module.cms.service.storage;

import cn.iocoder.txgy.module.cms.config.CmsStorageProperties;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.DOCUMENT_STORAGE_FAILED;

/**
 * 原件持久化适配层。数据库只保存 storage URI：local: 指向服务器磁盘，infra: 指向平台文件服务。
 * 生产环境将 mode 设为 infra 后，底层文件服务可直接配置为 MinIO/S3。
 */
@Service
@Slf4j
public class CmsDocumentStorageService {

    private static final String LOCAL_PREFIX = "local:";
    private static final String INFRA_PREFIX = "infra:";

    private final CmsStorageProperties properties;
    private final ObjectProvider<FileApi> fileApiProvider;

    public CmsDocumentStorageService(CmsStorageProperties properties, ObjectProvider<FileApi> fileApiProvider) {
        this.properties = properties;
        this.fileApiProvider = fileApiProvider;
    }

    public String store(long tenantId, String reviewId, String originalName, byte[] content) {
        if (content == null || content.length == 0) return null;
        try {
            String safeName = safeName(originalName);
            if ("infra".equalsIgnoreCase(properties.getMode())) {
                FileApi fileApi = fileApiProvider.getIfAvailable();
                if (fileApi == null) throw new IllegalStateException("FileApi unavailable");
                String url = fileApi.createFile(content, safeName,
                        "cms/knowledge/" + tenantId + "/" + reviewId, contentType(safeName));
                if (url == null || url.isBlank()) throw new IllegalStateException("FileApi returned empty URL");
                return INFRA_PREFIX + url;
            }
            Path root = localRoot();
            Path directory = root.resolve(Long.toString(tenantId)).resolve(reviewId).normalize();
            if (!directory.startsWith(root)) throw new IOException("Invalid storage directory");
            Files.createDirectories(directory);
            String fileName = digest(content).substring(0, 16) + "-" + safeName;
            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(directory)) throw new IOException("Invalid storage target");
            Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
            try {
                Files.write(temporary, content);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return LOCAL_PREFIX + root.relativize(target).toString().replace('\\', '/');
        } catch (Exception ex) {
            log.error("[store][CMS 原件持久化失败 tenantId={}, reviewId={}, mode={}]",
                    tenantId, reviewId, properties.getMode(), ex);
            throw exception(DOCUMENT_STORAGE_FAILED);
        }
    }

    public byte[] read(String storageUri) {
        if (storageUri == null || storageUri.isBlank()) return null;
        try {
            if (storageUri.startsWith(LOCAL_PREFIX)) {
                return readUnderRoot(localRoot(), storageUri.substring(LOCAL_PREFIX.length()));
            }
            String normalized = storageUri.replace('\\', '/');
            if (normalized.startsWith("/files/")) {
                return readUnderRoot(staticFilesRoot(), normalized.substring("/files/".length()));
            }
            return null;
        } catch (IOException ex) {
            log.warn("[read][CMS 本地原件读取失败 uri={}]", storageUri, ex);
            throw exception(DOCUMENT_STORAGE_FAILED);
        }
    }

    public String accessUrl(String storageUri) {
        if (storageUri == null || !storageUri.startsWith(INFRA_PREFIX)) return null;
        try {
            FileApi fileApi = fileApiProvider.getIfAvailable();
            if (fileApi == null) throw new IllegalStateException("FileApi unavailable");
            String url = storageUri.substring(INFRA_PREFIX.length());
            return fileApi.presignGetUrl(url, properties.getPresignSeconds()).getCheckedData();
        } catch (Exception ex) {
            log.warn("[accessUrl][CMS 原件预签名失败]", ex);
            throw exception(DOCUMENT_STORAGE_FAILED);
        }
    }

    private Path localRoot() throws IOException {
        Path root = Path.of(properties.getLocalRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private Path staticFilesRoot() {
        return Path.of(properties.getStaticFilesRoot()).toAbsolutePath().normalize();
    }

    private static byte[] readUnderRoot(Path root, String relative) throws IOException {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) throw new IOException("File not found");
        return Files.readAllBytes(target);
    }

    private static String safeName(String value) {
        String name = Objects.toString(value, "original.bin").strip();
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(separator + 1).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (name.isBlank()) name = "original.bin";
        return name.length() <= 120 ? name : name.substring(name.length() - 120);
    }

    private static String contentType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".txt")) return "text/plain; charset=UTF-8";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
        return "application/octet-stream";
    }

    private static String digest(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
