package cn.iocoder.txgy.module.bidding.service.externalfetch.channel;
// [依据] 设计文档 v0.6 §四（通用下载器落地）：GENERIC 通道由占位升级为真实 HTTP 下载。
// 参数以配置文件为准（bidding.crawl.http.*，CrawlHttpProperties）；HttpFetchClient 负责 GET/POST 抓取，
// 本通道按任务 url 取响应并判定形态：2xx 文本(HTML/JSON) 直存 / 2xx 二进制(PDF) 落临时文件（供 PDFBox 解析）；
// 非 2xx/网络异常抛错（计入重试），避免“假成功”。

import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.infra.config.CrawlHttpProperties;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.HttpFetchClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
/**
 * 通用 HTTP 下载通道（GENERIC）。
 *
 * <p>download(CrawlTaskDO) 读取 task.url 下载：成功→返回 body（文本原文或 PDF 字节+临时落盘路径）；
 * 失败→抛异常（由任务重试机制计失败）。落盘临时目录：bidding.crawl.http.download-tmp-dir（空=java.io.tmpdir）。
 */
@Slf4j
@Component
public class GenericHttpDownloadChannel implements DownloadChannelAdapter {

    public static final String CHANNEL_CODE = "GENERIC";

    @Resource
    private HttpFetchClient httpFetchClient;

    @Resource
    private CrawlHttpProperties props;

    /** 原文下载限速闸：通道是"下载公告原文文件"的执行点之一，必须与消费端共用同一闸位（合规频控唯一入口） */
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadGate crawlDownloadGate;

    @Override
    public String getChannelCode() {
        return CHANNEL_CODE;
    }

    @Override
    public DownloadResult download(CrawlTaskDO task) {
        String url = task == null ? null : task.getUrl();
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("下载任务缺少 url，taskId=" + (task == null ? "?" : task.getId()));
        }
        // 合规限速（2026-09-10）：下载原文文件前必须获得闸位——未到间隔即抛 NotDue（消费端负责把任务放回 PENDING 排队），
        // 绝不"先请求再判断"。批量/同步线程调用本通道时，调用方应改用 crawlDownloadGate.acquireBlocking(...) 等待到位。
        if (!crawlDownloadGate.tryAcquire(task.getSourceCode(), task.getId(), url)) {
            throw new cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadNotDueException(
                    crawlDownloadGate.nextDueAt(task.getSourceCode()), url);
        }
        byte[] bytes;
        try {
            bytes = httpFetchClient.getBytes(url);
        } catch (IOException e) {
            throw new IllegalStateException("下载失败 url=" + url + " : " + e.getMessage(), e);
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("下载返回空内容 url=" + url);
        }
        String text = sniffText(bytes);
        String contentType = sniffContentType(text);
        if ("PDF".equals(contentType)) {
            return pdfResult(persistPdf(url, bytes));
        }
        // HTML/JSON/其它按文本回
        return textResult(text);
    }

    /** 判定响应形态：前若干字节含 PDF 头 → PDF；否则按文本。 */
    private static String sniffContentType(String text) {
        return (text != null && (text.contains("%PDF") || text.startsWith("%PDF"))) ? "PDF" : "HTML";
    }

    /** 二进制是否可安全按 UTF-8 文本读（PDF 含 %PDF 头与不可读字节则走 PDF 分支）。 */
    private static String sniffText(byte[] bytes) {
        // 快速判定 PDF 魔数
        if (bytes.length >= 5) {
            String head = new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII);
            if (head.startsWith("%PDF")) {
                return null; // PDF：交由 PDF 分支
            }
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String persistPdf(String url, byte[] pdf) {
        CrawlHttpProperties p = props != null ? props : new CrawlHttpProperties();
        Path dir;
        try {
            dir = (p.getDownloadTmpDir() == null || p.getDownloadTmpDir().isEmpty())
                    ? Files.createTempDirectory("crawl-dl-")
                    : Files.createDirectories(Path.of(p.getDownloadTmpDir()));
        } catch (IOException e) {
            throw new IllegalStateException("无法创建下载临时目录: " + e.getMessage(), e);
        }
        String name = "dl-" + Integer.toHexString(url.hashCode()) + "-" + System.currentTimeMillis() + ".pdf";
        try {
            Path file = dir.resolve(name);
            Files.write(file, pdf, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("[download][url={}] PDF 落盘 {} ({} bytes)", url, file, pdf.length);
            return file.toString();
        } catch (IOException e) {
            throw new IllegalStateException("PDF 落盘失败: " + e.getMessage(), e);
        }
    }

    private static DownloadResult pdfResult(String filePath) {
        DownloadResult res = new DownloadResult();
        res.setContentRef(filePath);
        res.setContentType("PDF");
        res.setHttpStatus(200);
        return res;
    }

    private static DownloadResult textResult(String text) {
        DownloadResult res = new DownloadResult();
        res.setRawBody(text);
        res.setContentType("HTML");
        res.setHttpStatus(200);
        return res;
    }
}
