package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.infra.file.PdfBoxOpenDataLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 智慧标书审查 · 文件解析实现。
 *
 * <p>方案依据：§4.2 PARSE、§6.3（扫描件策略）。
 * <p>实现取舍（P0）：
 * <ol>
 *   <li>PDF 复用模块内既有 PDF 引擎 {@link PdfBoxOpenDataLoader}（PDFBox 封装），空结果即判定扫描件；</li>
 *   <li>DOCX 用「解压 + 去标签」提取正文，不引入 POI 依赖（P1 若需表格/样式再升级，方案 §8 P1）；</li>
 *   <li>其它类型按 UTF-8 文本直读；</li>
 *   <li><b>扫描件不做图片转写</b>（DeepSeek 无视觉模型），直接返回 EMPTY，由流水线按
 *       {@code BID_REVIEW_PARSE_EMPTY} 失败并提示用户上传文字版。</li>
 * </ol>
 */
@Slf4j
@Service
public class BidReviewParseServiceImpl implements BidReviewParseService {

    /** 单文件下载上限 30MB（防误传大包打满内存） */
    private static final int MAX_DOWNLOAD_BYTES = 30 * 1024 * 1024;

    /**
     * 文件服务内部地址：上传接口回传的 URL 是「宿主机视角」（形如 http://127.0.0.1:48080/admin-api/infra/file/...），
     * 在本服务容器里 127.0.0.1 指向容器自身，直接下载必然 ConnectException。
     * 因此当 URL 的 host 是 127.0.0.1/localhost/0.0.0.0 时，替换为容器网络内的服务地址（默认 http://server:48080）。
     */
    @Value("${bidding.review.file.base-url:http://server:48080}")
    private String internalFileBaseUrl;

    private static final Pattern XML_TAG = Pattern.compile("<[^>]+>");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** PDF 引擎（模块内既有实现：PDFBox 封装；与公告域共用同一套底层能力） */
    private final PdfBoxOpenDataLoader pdfLoader = new PdfBoxOpenDataLoader();

    @Override
    public ParseResult parse(String fileUrl, String fileName) {
        if (StrUtil.isBlank(fileUrl)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_FILE_EMPTY);
        }
        byte[] content = download(fileUrl);
        String lower = StrUtil.nullToEmpty(fileName).toLowerCase();
        String text;
        if (lower.endsWith(".pdf")) {
            text = parsePdf(content);
        } else if (lower.endsWith(".docx")) {
            text = parseDocx(content);
        } else if (lower.endsWith(".doc")) {
            // 旧版 .doc 是二进制格式，不做魔法解析：明确失败，避免抽出乱码当正文
            text = "";
        } else {
            text = new String(content, StandardCharsets.UTF_8);
        }
        text = StrUtil.trimToEmpty(text);
        return new ParseResult(text, text.isBlank() ? "EMPTY" : "TEXT");
    }

    /** 下载文件内容（走 HTTP 取流；失败即明确报错，不静默降级） */
    private byte[] download(String fileUrl) {
        String downloadUrl = resolveDownloadUrl(fileUrl);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_FILE_DOWNLOAD_FAILED,
                        "HTTP " + response.statusCode() + " " + downloadUrl);
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_FILE_EMPTY);
            }
            if (body.length > MAX_DOWNLOAD_BYTES) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_FILE_DOWNLOAD_FAILED, "文件超过 30MB");
            }
            return body;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[download][标书文件下载失败 url={} resolved={}]", fileUrl, downloadUrl, e);
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BID_REVIEW_FILE_DOWNLOAD_FAILED,
                    e.getClass().getSimpleName() + " " + downloadUrl);
        }
    }

    /**
     * 把文件地址解析成「容器内可下载的绝对地址」。
     *
     * <p>两种输入：
     * <ul>
     *   <li><b>宿主机视角地址</b>（127.0.0.1 / localhost / 0.0.0.0）：换成容器内可达地址，保留 path/query；</li>
     *   <li><b>其它</b>（生产域名、对象存储、容器服务名）：原样使用。</li>
     * </ul>
     *
     * <p>注（2026-09-22 清理「相对地址」分支）：原实现另有一个"相对地址"分支，注释称
     * "文件服务已改为返回 {@code /admin-api/infra/file/4/get/xxx}（见 sql/2026-09-20-infra-file-url-relative.sql）"。
     * 该前提经实测不成立：库内 {@code infra_file.url} <b>4457 条全为绝对地址、0 条相对</b>，
     * 且被引用的 SQL 脚本在仓库任何分支都不存在 —— 分支无数据可命中，留着只会用错误前提误导维护。
     * 删掉不丢能力：相对路径仍由下方 {@code host == null} 兜底分支按同样口径补全基址。
     */
    private String resolveDownloadUrl(String fileUrl) {
        try {
            String raw = StrUtil.trimToEmpty(fileUrl);
            if (raw.isEmpty()) {
                return raw;
            }
            String base = StrUtil.removeSuffix(internalFileBaseUrl.trim(), "/");
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if (host == null) {
                // 兜底：能 parse 出 scheme 但取不到 host（极少见），按相对地址处理
                return base + (raw.startsWith("/") ? raw : "/" + raw);
            }
            boolean localHost = "127.0.0.1".equals(host) || "localhost".equals(host) || "0.0.0.0".equals(host);
            if (!localHost) {
                return raw;
            }
            String path = StrUtil.nullToEmpty(uri.getRawPath());
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            return base + path + query;
        } catch (Exception e) {
            return fileUrl;
        }
    }

    private String parsePdf(byte[] content) {
        File temp = null;
        try {
            temp = File.createTempFile("bid-review-", ".pdf");
            Files.write(temp.toPath(), content);
            Object raw = pdfLoader.extract(temp);
            PdfBoxOpenDataLoader.Result result = raw instanceof PdfBoxOpenDataLoader.Result r ? r : null;
            if (result == null || StrUtil.isBlank(result.getText()) && (result.getTables() == null || result.getTables().isEmpty())) {
                return "";
            }
            StringBuilder sb = new StringBuilder(StrUtil.nullToEmpty(result.getText()));
            // 表格按行拼接保留（审查大量依赖报价表/业绩表）
            if (result.getTables() != null) {
                for (var table : result.getTables()) {
                    if (table == null) {
                        continue;
                    }
                    sb.append('\n').append(String.join(" | ", table));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[parsePdf][PDF 解析异常，按无文字层处理]", e);
            return "";
        } finally {
            if (temp != null) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    /** DOCX：解压取 word/document.xml，去标签 + 段落符还原（P0 简化实现，见类注释） */
    private String parseDocx(byte[] content) {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = new ByteArrayInputStream(content);
             ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                String withBreaks = xml
                        .replace("</w:p>", "\n")
                        .replace("<w:tab/>", "\t")
                        .replace("<w:br/>", "\n");
                String text = XML_TAG.matcher(withBreaks).replaceAll("");
                sb.append(text);
            }
        } catch (Exception e) {
            log.warn("[parseDocx][DOCX 解析异常，按无文字层处理]", e);
            return "";
        }
        // 去掉 XML 实体残留，压缩连续空行
        return sb.toString()
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\n{3,}", "\n\n");
    }
}
