package cn.iocoder.txgy.module.bidding.service.externalfetch.fetch;
// [依据] 下载器 HTTP 抓取客户端（JDK HttpClient）：HTML/JSON(POST)/二进制 三通道；
//       参数全部来自配置文件 bidding.crawl.http（CrawlHttpProperties），配置文件为准不改代码。
// 2026-09-10 缺陷修复（用户口径「反爬策略不足、异常处理缺失、数据完整性校验」）：
//   · 状态分类：2xx=成功；404/410/401=永久失败（不重试）；403/429/503=反爬拦截（长冷却）；
//     408/5xx/超时=瞬时失败（可退避重试）——统一由 FetchException 三态表达；
//   · 尊重 Retry-After（429/503 常见），把它作为建议等待时长带到上层；
//   · 单文件上限 max-download-bytes：流式读取边读边计数，超限立即中断，防被灌注大文件撑爆内存/带宽；
//   · 每次请求记录 耗时/字节数（下载速度监控的数据源，见 CrawlDownloadAuditor）；
//   · 200 但命中反爬特征词（验证码/访问频繁等）也会被标记，避免把拦截页当正文入库。

import cn.iocoder.txgy.module.bidding.infra.config.CrawlHttpProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 下载器 HTTP 客户端（读侧；单例复用连接，懒建）。
 *
 * <p>配置项（bidding.crawl.http.*，见 CrawlHttpProperties）：
 * connect-timeout-seconds / read-timeout-seconds / follow-redirects / user-agent / referer /
 * download-timeout-seconds / download-tmp-dir / max-download-bytes / speed-warn-bps / speed-slow-warn-bps。
 */
@Slf4j
@Component
public class HttpFetchClient {

    /** 反爬特征词（命中即视为拦截页：绝不能把验证码/限制提示当公告正文落库） */
    private static final String[] ANTI_CRAWL_MARKERS = {
            "验证码", "captcha", "访问过于频繁", "访问频繁", "请稍后再试", "请求过于频繁",
            "Access Denied", "Forbidden", "系统繁忙", "您的访问出现异常"
    };

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

    /** 抓取结果（下载速度监控与完整性校验的输入） */
    public static class FetchOutcome {
        private final String url;
        private final int statusCode;
        private final long durationMs;
        private final Integer retryAfterSeconds;
        private final byte[] bytes;

        FetchOutcome(String url, int statusCode, long durationMs, Integer retryAfterSeconds, byte[] bytes) {
            this.url = url;
            this.statusCode = statusCode;
            this.durationMs = durationMs;
            this.retryAfterSeconds = retryAfterSeconds;
            this.bytes = bytes;
        }

        public String getUrl() {
            return url;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public Integer getRetryAfterSeconds() {
            return retryAfterSeconds;
        }

        public byte[] getBytes() {
            return bytes;
        }

        /** 字节数（下载量） */
        public int getByteSize() {
            return bytes == null ? 0 : bytes.length;
        }

        /** 下载速率（字节/秒；耗时 0 时按 1ms 兜底避免除零） */
        public long getSpeedBps() {
            long ms = Math.max(1L, durationMs);
            return getByteSize() * 1000L / ms;
        }

        public String getText() {
            return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }

        /** 是否疑似反爬拦截页（正文命中特征词） */
        public boolean antiCrawlSuspected() {
            String text = getText();
            if (text == null) {
                return false;
            }
            String head = text.length() > 4000 ? text.substring(0, 4000) : text;
            for (String marker : ANTI_CRAWL_MARKERS) {
                if (head.contains(marker)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** GET 文本（HTML/JSON 响应） */
    public String getText(String url) throws IOException {
        return fetchText(url).getText();
    }

    /** GET 文本 + 抓取元信息（耗时/字节数/状态码；下载监控用） */
    public FetchOutcome fetchText(String url) throws IOException {
        return fetchBytes(url);
    }

    /** GET 文本 + 自定义头（API 源鉴权头；见 ApiFetchDriver） */
    public String getTextWithHeaders(String url, Map<String, String> headers) throws IOException {
        return fetchBytes(url, headers).getText();
    }

    /** POST JSON（列表接口），返回响应文本 */
    public String postJson(String url, String jsonBody) throws IOException {
        return postJsonWithHeaders(url, jsonBody, null);
    }

    /** POST JSON + 自定义头（API 源鉴权：X-Client-Id/X-Signature 等静态头；见 ApiFetchDriver） */
    public String postJsonWithHeaders(String url, String jsonBody, Map<String, String> headers) throws IOException {
        HttpRequest.Builder builder = base(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json");
        applyHeaders(builder, headers);
        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build();
        return sendForBytes(request, url, false).getText();
    }

    /** GET 二进制（PDF 等） */
    public byte[] getBytes(String url) throws IOException {
        return fetchBytes(url).getBytes();
    }

    /** GET 二进制 + 抓取元信息（PDF 完整性校验与速度监控用） */
    public FetchOutcome fetchBytes(String url) throws IOException {
        return fetchBytes(url, null);
    }

    private FetchOutcome fetchBytes(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder builder = base(URI.create(url));
        applyHeaders(builder, headers);
        return sendForBytes(builder.GET().build(), url, true);
    }

    /**
     * 发送请求并读取响应体（带单文件上限与耗时统计），按状态码分类抛出 FetchException。
     *
     * @param capBytes 是否对响应体施加 max-download-bytes 上限（二进制/原文下载=true；列表接口=false）
     */
    private FetchOutcome sendForBytes(HttpRequest request, String url, boolean capBytes) throws IOException {
        long start = System.currentTimeMillis();
        HttpResponse<InputStream> response;
        try {
            response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException.Transient("HTTP 请求被中断: " + e.getMessage(), null, null, e);
        } catch (IOException e) {
            // 连接超时/重置/UnknownHost 等：归入瞬时失败（可退避重试）
            throw new FetchException.Transient("HTTP 请求失败: " + e.getMessage(), null, null, e);
        }
        int status = response.statusCode();
        Integer retryAfter = retryAfterSeconds(response);
        long cap = capBytes ? Math.max(1L, cfg().getMaxDownloadBytes()) : Long.MAX_VALUE;
        byte[] body;
        try (InputStream in = response.body()) {
            body = readCapped(in, cap, url);
        } catch (IOException e) {
            throw new FetchException.Transient("读取响应体失败: " + e.getMessage(), status, retryAfter, e);
        }
        long durationMs = System.currentTimeMillis() - start;

        if (status >= 200 && status < 300) {
            FetchOutcome outcome = new FetchOutcome(url, status, durationMs, retryAfter, body);
            if (outcome.antiCrawlSuspected()) {
                // 200 + 拦截页特征：按反爬处理（不落库），交给上层退避重试或转人工
                throw new FetchException.AntiCrawl("响应命中反爬特征（疑似验证码/限流页） url=" + url,
                        status, retryAfter);
            }
            return outcome;
        }
        String brief = "HTTP " + status + " url=" + url;
        if (status == 404 || status == 410 || status == 401) {
            throw new FetchException.Permanent("资源不存在或无权访问（" + brief + "）", status);
        }
        if (status == 403 || status == 429 || status == 503) {
            throw new FetchException.AntiCrawl("疑似反爬拦截（" + brief + "，Retry-After=" + retryAfter + "s）", status, retryAfter);
        }
        if (status == 408 || status >= 500) {
            throw new FetchException.Transient("服务端瞬时异常（" + brief + "）", status, retryAfter);
        }
        throw new FetchException.Transient("非预期状态码（" + brief + "）", status, retryAfter);
    }

    /** 流式读取并强制上限：超限立即中断（不把整个大文件读进内存） */
    private static byte[] readCapped(InputStream in, long cap, String url) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > cap) {
                throw new FetchException.Transient(
                        "响应体超过单文件上限 " + cap + " 字节（已读 " + total + "）url=" + url, null, null);
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /** 解析 Retry-After（秒；HTTP-date 形式不支持时返回 null，不做臆测） */
    private static Integer retryAfterSeconds(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").map(v -> {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }).orElse(null);
    }

    private static void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        headers.forEach((k, v) -> {
            if (k != null && v != null) {
                builder.header(k, v);
            }
        });
    }

    /** 公共请求基座：读超时（下载用 download-timeout）+ UA + 可选 Referer + Accept */
    private HttpRequest.Builder base(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(cfg().getReadTimeoutSeconds(), cfg().getDownloadTimeoutSeconds())))
                .header("User-Agent", cfg().getUserAgent())
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
    }
}
