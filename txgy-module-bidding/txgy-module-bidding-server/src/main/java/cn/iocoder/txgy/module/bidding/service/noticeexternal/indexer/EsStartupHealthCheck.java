package cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer;

// [依据] 2026-09-10 用户指令：ES 必须可用才能启动（fail-fast）。
// 此前 bidding.es.enabled 缺省关闭（DevDisabled/LogOnly 降级装配），服务无 ES 也能启动，
// 审核通过的公告全部不建索引、keyword 检索不可用，问题会一路带到线上。
// 本组件在 bidding.es.enabled=true 时装配：应用就绪前探活 ES 集群，
// 不可达（连接失败/401/5xx）直接抛异常终止启动；确需无 ES 降级运行须显式 bidding.es.enabled=false。

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * ES 启动探活（ApplicationRunner：全部 Bean 就绪后、应用对外就绪前执行；抛异常=启动失败）。
 * 与 EsExternalNoticeIndexNotifier/EsExternalNoticeSearchEngine 共用 bidding.es.* 配置与 Basic 认证；
 * 探活请求 GET {uris}/_cluster/health?timeout=3s（轻量只读）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "bidding.es", name = "enabled", havingValue = "true")
public class EsStartupHealthCheck implements ApplicationRunner {

    @Value("${bidding.es.uris:http://127.0.0.1:9200}")
    private String uris;

    @Value("${bidding.es.username:elastic}")
    private String username;

    @Value("${bidding.es.password:}")
    private String password;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String base = uris.replaceAll("/+$", "");
        URI healthUri = URI.create(base + "/_cluster/health?timeout=3s");
        HttpRequest.Builder builder = HttpRequest.newBuilder(healthUri)
                .timeout(Duration.ofSeconds(8))
                .GET();
        if (StringUtils.hasText(password)) {
            String token = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[EsStartupHealthCheck][OK] ES 集群可达 {} -> {} {}", base,
                        response.statusCode(), truncate(response.body(), 300));
                return;
            }
            throw new IllegalStateException("ES 集群健康检查 HTTP " + response.statusCode()
                    + " -> " + truncate(response.body(), 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ES 启动探活被中断 uri=" + healthUri, e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("ES 集群不可达（" + base + "），拒绝启动："
                    + "审核通过的公告将无法建立全文索引、keyword 检索不可用。"
                    + "请先启动 ES 集群；确需降级运行请显式配置 bidding.es.enabled=false。"
                    + "原因: " + e.getMessage(), e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
