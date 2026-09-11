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

    // ===== 2026-09-10 新增：下载监控与数据完整性（用户口径「严格监测下载速度/文件完整性」）=====

    /** 单文件下载上限（字节，默认 50MB）：流式读取超限即中断，防被灌注大文件撑爆内存与带宽 */
    private long maxDownloadBytes = 50L * 1024 * 1024;

    /** 下载速率上限告警（字节/秒，默认 2MB/s）：超过可能意味着带宽异常占用（或被镜像加速），记 WARN 并落审计 */
    private long speedWarnBps = 2L * 1024 * 1024;

    /** 下载速率下限告警（字节/秒，默认 2KB/s）：长期低于可能被限速或站点异常，记 WARN 并落审计 */
    private long speedSlowWarnBps = 2L * 1024;

    /** 是否遵循 robots.txt（默认 true：抓取前检查 Disallow；robots.txt 不可达时放行并记审计） */
    private boolean robotsEnabled = true;

    /** robots.txt 缓存时长（分钟，默认 360=6 小时，避免每任务都去取 robots） */
    private int robotsCacheMinutes = 360;
}
