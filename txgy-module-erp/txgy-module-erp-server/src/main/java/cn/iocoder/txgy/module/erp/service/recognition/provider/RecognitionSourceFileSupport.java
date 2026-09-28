package cn.iocoder.txgy.module.erp.service.recognition.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 单据识别源文件下载与 PDF 转图片公用工具。
 *
 * <p>从 {@link TesseractDocumentRecognitionProvider} 抽出，供 Tesseract 与 QwenVision 共用，
 * 避免重复实现 file://、内网 loopback base-url 重写、pdftoppm 调用等逻辑。</p>
 */
@Slf4j
@Component
public class RecognitionSourceFileSupport {

    @Resource
    private RestTemplate restTemplate;

    @Value("${erp.recognition.source-file-base-url:}")
    private String sourceFileBaseUrl;

    /** 下载源文件到工作目录；支持 file://、http(s)://；loopback host 时按 source-file-base-url 重写 */
    public Path downloadSourceFile(String sourceFileUrl, Path workDir) throws IOException {
        if (!StringUtils.hasText(sourceFileUrl)) {
            throw new IllegalArgumentException("识别文件地址不能为空");
        }
        URI uri = parseUri(sourceFileUrl);
        String fileName = sourceFileUrl.substring(sourceFileUrl.lastIndexOf('/') + 1);
        fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            fileName = "source.bin";
        }
        Path target = workDir.resolve(fileName).normalize();
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            Files.copy(Path.of(uri), target);
            return target;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("不支持的文件地址协议：" + uri.getScheme());
        }
        ResponseEntity<byte[]> response = restTemplate.getForEntity(resolveDownloadUri(uri), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                || response.getBody().length == 0) {
            throw new IllegalStateException("无法下载待识别文件：" + response.getStatusCode());
        }
        Files.write(target, response.getBody(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return target;
    }

    /** PDF 转图片：pdftoppm -png -r <dpi>，返回按页号排序的 PNG 列表 */
    public List<Path> convertPdfToImages(Path pdfFile, Path workDir, int dpi, long timeoutSeconds) throws IOException {
        Path prefix = workDir.resolve("page");
        runCommand(List.of("pdftoppm", "-png", "-r", String.valueOf(dpi),
                        pdfFile.toString(), prefix.toString()),
                "PDF 转图片失败", timeoutSeconds);
        try (var stream = Files.list(workDir)) {
            List<Path> pages = stream
                    .filter(path -> path.getFileName().toString().startsWith("page-")
                            && path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            if (pages.isEmpty()) {
                throw new IllegalStateException("PDF 未生成可识别的页面图片");
            }
            return pages;
        }
    }

    /** 执行外部命令，超时强杀；redirectErrorStream 合并 stderr 到 stdout */
    public String runCommand(List<String> command, String errorPrefix, long timeoutSeconds) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(errorPrefix + "：执行超时（" + timeoutSeconds + " 秒）");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(errorPrefix + "：" + output.trim());
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException(errorPrefix + "：线程被中断", e);
        }
    }

    /** 判断是否 PDF（按扩展名） */
    public boolean isPdf(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /** 递归删除临时目录 */
    public void deleteDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("删除 OCR 临时文件失败：{}", path, e);
                }
            });
        } catch (IOException e) {
            log.debug("删除 OCR 临时目录失败：{}", directory, e);
        }
    }

    private URI resolveDownloadUri(URI sourceUri) {
        if (!StringUtils.hasText(sourceFileBaseUrl)
                || sourceUri.getHost() == null
                || !isLoopbackHost(sourceUri.getHost())) {
            return sourceUri;
        }
        try {
            URI baseUri = URI.create(sourceFileBaseUrl.trim());
            // 保留原始 path 的百分号编码。使用 URI 多参数构造器会重新处理中文路径，
            // Apache HttpClient 可能将文件名误解析为 query，导致 infra 只收到目录路径。
            String base = baseUri.toASCIIString();
            String rawPath = sourceUri.getRawPath();
            if (!StringUtils.hasText(rawPath)) {
                throw new IllegalStateException("ERP OCR 文件访问地址缺少路径：" + sourceUri);
            }
            if (base.endsWith("/") && rawPath.startsWith("/")) {
                base = base.substring(0, base.length() - 1);
            } else if (!base.endsWith("/") && !rawPath.startsWith("/")) {
                base = base + "/";
            }
            StringBuilder resolved = new StringBuilder(base).append(rawPath);
            if (sourceUri.getRawQuery() != null) {
                resolved.append('?').append(sourceUri.getRawQuery());
            }
            if (sourceUri.getRawFragment() != null) {
                resolved.append('#').append(sourceUri.getRawFragment());
            }
            return URI.create(resolved.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ERP OCR 文件访问地址配置不正确：" + sourceFileBaseUrl, e);
        }
    }

    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("文件地址不是有效 URL：" + value, e);
        }
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }
}
