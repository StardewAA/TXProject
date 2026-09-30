package cn.iocoder.txgy.module.cms.service.storage;

import cn.iocoder.txgy.module.cms.config.CmsStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.PUBLICATION_URL_FETCH_FAILED;

/**
 * URL 解析后的正文快照。使用无头 Chrome/Edge 把清洗后的静态 HTML 固化为 PDF，短期令牌只用于
 * “解析 -> 用户确认 -> 提交”之间传递；正式提交后 PDF 会立即转入 CmsDocumentStorageService。
 */
@Service
@Slf4j
public class CmsUrlSnapshotService {

    private static final int MAX_STAGED = 100;
    private static final long MAX_PDF_BYTES = 30L * 1024 * 1024;
    private final CmsStorageProperties properties;
    private final Map<String, StagedSnapshot> staged = new ConcurrentHashMap<>();

    public CmsUrlSnapshotService(CmsStorageProperties properties) {
        this.properties = properties;
    }

    public String stage(long tenantId, Long userId, String title, String sourceUrl, String text) {
        cleanup();
        if (staged.size() >= MAX_STAGED) {
            staged.entrySet().stream().min(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                    .ifPresent(entry -> staged.remove(entry.getKey()));
        }
        byte[] pdf = renderPdf(title, sourceUrl, text);
        String token = UUID.randomUUID().toString();
        staged.put(token, new StagedSnapshot(tenantId, userId, sourceUrl,
                safePdfName(title), pdf, Instant.now().plus(Duration.ofMinutes(properties.getSnapshotTokenMinutes()))));
        return token;
    }

    public Snapshot consume(long tenantId, Long userId, String token, String sourceUrl) {
        if (token == null || token.isBlank()) return null;
        StagedSnapshot snapshot = staged.remove(token);
        if (snapshot == null || snapshot.expiresAt().isBefore(Instant.now())
                || snapshot.tenantId() != tenantId || !Objects.equals(snapshot.userId(), userId)
                || !Objects.equals(normalizeUrl(snapshot.sourceUrl()), normalizeUrl(sourceUrl))) {
            throw exception(PUBLICATION_URL_FETCH_FAILED);
        }
        return new Snapshot(snapshot.filename(), snapshot.pdf());
    }

    private byte[] renderPdf(String title, String sourceUrl, String text) {
        Path directory = null;
        try {
            String browser = resolveBrowser();
            directory = Files.createTempDirectory("cms-url-snapshot-");
            Path html = directory.resolve("source.html");
            Path pdf = directory.resolve("source.pdf");
            Path profile = directory.resolve("browser-profile");
            Path logFile = directory.resolve("browser.log");
            Files.writeString(html, snapshotHtml(title, sourceUrl, text), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(browser,
                    "--headless=new", "--disable-gpu", "--no-sandbox", "--disable-extensions",
                    "--disable-background-networking", "--run-all-compositor-stages-before-draw",
                    "--user-data-dir=" + profile.toAbsolutePath(),
                    "--print-to-pdf=" + pdf.toAbsolutePath(), "--print-to-pdf-no-header",
                    html.toUri().toString())
                    .redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
            boolean completed = process.waitFor(Math.max(10, properties.getSnapshotTimeoutSeconds()), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("Snapshot browser timeout");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(pdf)) {
                String detail = Files.isRegularFile(logFile) ? Files.readString(logFile) : "";
                throw new IOException("Snapshot browser failed: " + detail.substring(0, Math.min(500, detail.length())));
            }
            long size = Files.size(pdf);
            if (size == 0 || size > MAX_PDF_BYTES) throw new IOException("Invalid snapshot size: " + size);
            return Files.readAllBytes(pdf);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw exception(PUBLICATION_URL_FETCH_FAILED);
        } catch (IOException ex) {
            log.warn("[renderPdf][URL PDF 快照生成失败]", ex);
            throw exception(PUBLICATION_URL_FETCH_FAILED);
        } finally {
            deleteTree(directory);
        }
    }

    private String resolveBrowser() throws IOException {
        if (properties.getSnapshotBrowser() != null && !properties.getSnapshotBrowser().isBlank()) {
            Path configured = Path.of(properties.getSnapshotBrowser()).toAbsolutePath().normalize();
            if (Files.isRegularFile(configured)) return configured.toString();
            throw new IOException("Configured snapshot browser not found");
        }
        String[] candidates = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? new String[]{"C:/Program Files/Google/Chrome/Application/chrome.exe",
                "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
                "C:/Program Files/Microsoft/Edge/Application/msedge.exe"}
                : new String[]{"/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser"};
        for (String candidate : candidates) if (Files.isRegularFile(Path.of(candidate))) return candidate;
        throw new IOException("Chrome/Edge not found; configure CMS_SNAPSHOT_BROWSER");
    }

    private static String snapshotHtml(String title, String sourceUrl, String text) {
        return """
                <!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">
                <style>@page{size:A4;margin:18mm 16mm}body{font-family:"Microsoft YaHei","Noto Sans CJK SC",sans-serif;color:#1f2937;line-height:1.75;font-size:12px}h1{font-size:20px;text-align:center;margin:0 0 12px}.source{font-size:10px;color:#64748b;border-bottom:1px solid #cbd5e1;padding-bottom:10px;margin-bottom:18px;word-break:break-all}.content{white-space:pre-wrap;word-break:break-word}</style>
                </head><body><h1>%s</h1><div class="source">原始来源：%s</div><div class="content">%s</div></body></html>
                """.formatted(HtmlUtils.htmlEscape(Objects.toString(title, "原文快照")),
                HtmlUtils.htmlEscape(Objects.toString(sourceUrl, "")),
                HtmlUtils.htmlEscape(Objects.toString(text, "")));
    }

    private void cleanup() {
        Instant now = Instant.now();
        staged.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private static String normalizeUrl(String value) {
        return Objects.toString(value, "").strip();
    }

    private static String safePdfName(String title) {
        String name = Objects.toString(title, "原文快照").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (name.isBlank()) name = "原文快照";
        if (name.length() > 100) name = name.substring(0, 100);
        return name + ".pdf";
    }

    private static void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private record StagedSnapshot(long tenantId, Long userId, String sourceUrl, String filename,
                                  byte[] pdf, Instant expiresAt) { }
    public record Snapshot(String filename, byte[] content) { }
}
