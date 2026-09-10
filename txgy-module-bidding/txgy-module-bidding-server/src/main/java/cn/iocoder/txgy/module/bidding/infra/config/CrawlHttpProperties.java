package cn.iocoder.txgy.module.bidding.infra.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 爬虫下载器/解析器参数配置（配置文件为准，多人协作不改代码）。
 *
 * <p>读取键：bidding.crawl.http.*（application.yaml 根级 bidding 段）。
 * 供 {@code HttpFetchClient}（下载客户端）与 GenericHttpDownloadChannel 在启动时读取覆盖默认值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "bidding.crawl.http")
public class CrawlHttpProperties {

    /** 连接超时（秒），默认 10 */
    private int connectTimeoutSeconds = 10;

    /** 单请求读超时（秒），默认 20 */
    private int readTimeoutSeconds = 20;

    /** 是否跟随重定向（默认 true = NORMAL 级别，跨域同 HTTP 跟随） */
    private boolean followRedirects = true;

    /** User-Agent（默认浏览器 UA，与历史 HttpFetchClient 常量一致） */
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";

    /** 可选的 Referer 注入（部分源站防盗链需要；空=不注入） */
    private String referer = "";

    /** PDF/附件下载超时（秒），默认 60 */
    private int downloadTimeoutSeconds = 60;

    /** 下载临时目录（PDF 二进制 → PDFBox 解析前落盘）；空=java.io.tmpdir */
    private String downloadTmpDir = "";
}
