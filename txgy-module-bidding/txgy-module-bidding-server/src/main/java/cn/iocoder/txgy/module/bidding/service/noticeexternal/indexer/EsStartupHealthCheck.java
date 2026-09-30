package cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer;

// [依据] 2026-09-10 用户指令：ES 必须可用才能启动（fail-fast）。
// 此前 bidding.es.enabled 缺省关闭（DevDisabled/LogOnly 降级装配），服务无 ES 也能启动，
// 审核通过的公告全部不建索引、keyword 检索不可用，问题会一路带到线上。
// 本组件在 bidding.es.enabled=true 时装配：应用就绪前探活 ES 集群，
// 不可达（连接失败/401/5xx）直接抛异常终止启动；确需无 ES 降级运行须显式 bidding.es.enabled=false。

import cn.iocoder.txgy.module.bidding.service.noticeexternal.search.ExternalNoticeIndexMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * ES 启动探活（ApplicationRunner：全部 Bean 就绪后、应用对外就绪前执行；抛异常=启动失败）。
 * 与 EsExternalNoticeIndexNotifier/EsExternalNoticeSearchEngine 共用 bidding.es.* 配置与 Basic 认证；
 * 探活请求 GET {uris}/_cluster/health?timeout=3s（轻量只读）。
 *
 * <p><b>mapping 结构校验（2026-09-22 新增）</b>：探活通过后再校验索引 mapping 与
 * {@link cn.iocoder.txgy.module.bidding.service.noticeexternal.search.ExternalNoticeIndexMapping}
 * 是否一致（重点：text 字段是否带 ngram 分析器）。
 *
 * <p><b>为什么需要它</b>：索引结构是"代码定义 + 运行时索引"两份状态，二者靠人工同步（重建索引）。
 * 一旦代码改了检索方式而忘了重建索引，就会出现**代码是新的、索引是旧的**——
 * 检索行为悄悄退化（例：退回中文单字 OR 匹配，"搜炼化返回 1050 条但 0 条真含"），
 * 而且**不报任何错**，直到用户反馈才发现。这正是本次 ES 改造问题的成因，故加自动校验使失效可见。
 *
 * <p><b>为什么只 WARN 不阻断启动</b>：mapping 不一致不影响进程可用性（服务照常提供其它接口），
 * 阻断启动的代价（全站不可用）远大于收益。检出后由运维按告警执行重建（见
 * {@code sql/tools/rebuild-external-notice-index.js}）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "bidding.es", name = "enabled", havingValue = "true")
public class EsStartupHealthCheck implements ApplicationRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                verifyIndexMapping(base);
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

    /**
     * 校验索引 mapping 与代码定义（{@link ExternalNoticeIndexMapping#TEXT_FIELDS}）是否一致。
     *
     * <p>检查项（只做"会导致检索语义漂移"的关键检查，不做全量 diff 以免误报）：
     * <ol>
     *   <li>索引不存在 → WARN（首次部署、尚未建索引的正常情形，或重建中途）；</li>
     *   <li>代码声明的 text 字段，在索引里**缺少 ngram 分析器** → WARN（检索会退回单字 OR，最严重）；</li>
     *   <li>索引里多出非预期的 text 字段 → INFO（历史遗留字段，无害）。</li>
     * </ol>
     *
     * <p>任何异常都只记日志、不上抛：校验是"附加可见性"，不得影响启动结果。
     */
    private void verifyIndexMapping(String base) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(
                            URI.create(base + "/" + ExternalNoticeIndexMapping.INDEX + "/_mapping"))
                    .timeout(Duration.ofSeconds(8)).GET();
            if (StringUtils.hasText(password)) {
                String token = Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes(StandardCharsets.UTF_8));
                b.header("Authorization", "Basic " + token);
            }
            HttpResponse<String> resp = httpClient.send(b.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 404) {
                log.warn("[EsStartupHealthCheck][mapping] 索引 {} 不存在——关键词检索会返回空集；"
                                + "请执行 sql/tools/rebuild-external-notice-index.js --apply 建立索引",
                        ExternalNoticeIndexMapping.INDEX);
                return;
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("[EsStartupHealthCheck][mapping] 读取 mapping 失败 HTTP {} -> {}",
                        resp.statusCode(), truncate(resp.body(), 300));
                return;
            }
            JsonNode root = OBJECT_MAPPER.readTree(resp.body());
            JsonNode props = root.path(ExternalNoticeIndexMapping.INDEX)
                    .path("mappings").path("properties");
            if (props.isMissingNode() || props.isEmpty()) {
                log.warn("[EsStartupHealthCheck][mapping] 索引 {} 无 properties（mapping 为空）",
                        ExternalNoticeIndexMapping.INDEX);
                return;
            }
            List<String> missingAnalyzer = new ArrayList<>();
            for (String field : ExternalNoticeIndexMapping.TEXT_FIELDS) {
                JsonNode node = props.path(field);
                if (node.isMissingNode()) {
                    missingAnalyzer.add(field + "(字段缺失)");
                } else if (!ExternalNoticeIndexMapping.INDEX_ANALYZER
                        .equals(node.path("analyzer").asText())) {
                    missingAnalyzer.add(field + "(analyzer=" + node.path("analyzer").asText("无") + ")");
                }
            }
            if (!missingAnalyzer.isEmpty()) {
                log.warn("[EsStartupHealthCheck][mapping] ⚠ 索引 {} 与代码定义不一致：{} 个字段未使用 ngram 分析器 {}。"
                                + "后果：中文关键词检索会退回「单字 OR」——搜「炼化」会命中只含「化」的公告，"
                                + "结果宽泛且不相关。请执行 sql/tools/rebuild-external-notice-index.js --apply 重建索引。",
                        ExternalNoticeIndexMapping.INDEX, missingAnalyzer.size(),
                        truncate(String.join(", ", missingAnalyzer), 500));
            } else {
                log.info("[EsStartupHealthCheck][mapping][OK] 索引 {} mapping 与代码一致（{} 个字段均为 {}）",
                        ExternalNoticeIndexMapping.INDEX,
                        ExternalNoticeIndexMapping.TEXT_FIELDS.length,
                        ExternalNoticeIndexMapping.INDEX_ANALYZER);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[EsStartupHealthCheck][mapping] 校验被中断");
        } catch (Exception e) {
            // 校验是附加可见性，绝不影响启动（不上抛）
            log.warn("[EsStartupHealthCheck][mapping] 校验异常（忽略，不影响启动）: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
