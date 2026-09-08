package cn.iocoder.txgy.module.bidding.service.crawlexternal.fetch;
// [依据] 下载器 HTTP 抓取客户端（JDK HttpClient）：HTML/JSON(POST)/二进制 三通道；
//       参数全部来自配置文件 bidding.crawl.http（CrawlHttpProperties），配置文件为准不改代码；
//       与 DownloadChannelAdapter 的差异：本客户端为“抓取原文”，通道层负责按任务持久化策略。

import cn.iocoder.txgy.module.bidding.infra.config.CrawlHttpProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
/**
 * 下载器 HTTP 客户端（读侧；单例复用连接，懒建）。
 *
 * <p>配置项（bidding.crawl.http.*，见 CrawlHttpProperties）：
 * connect-timeout-seconds / read-timeout-seconds / follow-redirects / user-agent / referer / download-timeout-seconds / download-tmp-dir。
 */
@Slf4j
@Component
public class HttpFetchClient {

    @Resource
    private CrawlHttpProperties props;

    private volatile HttpClient http;

    private CrawlHttpProperties cfg() {
        return props != null ? props : new CrawlHttpProperties();
    }

    private HttpClient client() {
        HttpClient c = http;
        if (c == null) {
            synchronized (this) {
                c = http;
                if (c == null) {
                    CrawlHttpProperties p = cfg();
                    HttpClient.Builder b = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(p.getConnectTimeoutSeconds()))
                            .followRedirects(p.isFollowRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);
                    c = b.build();
                    http = c;
                }
            }
        }
        return c;
    }

    private Duration readTimeout() {
        return Duration.ofSeconds(cfg().getReadTimeoutSeconds());
    }

    /** GET 文本（HTML/JSON 响应） */
    public String getText(String url) throws IOException {
        HttpRequest request = base(URI.create(url)).GET().build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("GET " + url + " -> HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** POST JSON（列表接口），返回响应文本 */
    public String postJson(String url, String jsonBody) throws IOException {
        HttpRequest request = base(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("POST " + url + " -> HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** GET 二进制（PDF 等） */
    public byte[] getBytes(String url) throws IOException {
        HttpRequest request = base(URI.create(url)).GET().build();
        HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("GET " + url + " -> HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** 公共请求基座：读超时 + UA + 可选 Referer + Accept */
    private HttpRequest.Builder base(URI uri) {
        HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                .timeout(readTimeout())
                .header("User-Agent", cfg().getUserAgent())
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        String ref = cfg().getReferer();
        if (ref != null && !ref.isEmpty()) {
            rb.header("Referer", ref);
        }
        return rb;
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) throws IOException {
        try {
            return client().send(request, bodyHandler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP 请求被中断: " + e.getMessage(), e);
        }
    }
}
