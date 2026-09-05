package cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer;
// 规范依据：设计文档 v0.6 §7.6/§15.6（external 审核转 ACTIVE → 事件链建 ES 索引；§7.6 补偿扫描为 P2 未实现：失败仅记日志、需人工重放；索引名沿用内部公告 bidding-notice 风格 = bidding-notice-external）
// 说明：本地已启动 ES 8.10（9200，xpack 安全）→ 用 JDK HttpClient 原生 REST 直连（starter-mq 事件链接入后本类改为消费者调用同一 REST 即可，幂等 PUT 天然支持补偿重放）。
// 安全：凭据不写死，走 ${ES_USERNAME:elastic}/${ES_PASSWORD:}（环境注入）；bidding.es.debug=true 时打印请求/响应摘要（ESDebug 开关）。

import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ES 外部公告索引通知器：审核通过（ACTIVE）时把主档可检索字段写入
 * <code>bidding-notice-external</code> 索引（PUT /_doc/{id}，幂等覆盖）。
 *
 * <p>失败语义：任何异常上抛 IllegalStateException —— 调用方（approveExternal）已 try/catch
 * 旁路不阻断审核并记日志（注意：§7.6 补偿扫描任务尚未实现，失败仅记日志，需人工复核后重放）。
 * <p>纯文本来源：PDF 形态取 extraction_text（OpenDataLoader 抽取），HTML 形态用 jsoup 去标签取正文；
 * 均截断到 MAX_TEXT_CHARS 防超大文档。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "bidding.es", name = "enabled", havingValue = "true", matchIfMissing = false)
public class EsExternalNoticeIndexNotifier implements ExternalNoticeIndexNotifier {

    /** 索引名：沿用内部公告 bidding-notice 的短横线风格（bidding_notice_external → bidding-notice-external） */
    static final String INDEX = "bidding-notice-external";

    /** 入库正文字符上限（防超大 HTML/抽取文本撑爆 ES 文档） */
    private static final int MAX_TEXT_CHARS = 100_000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ES_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Value("${bidding.es.uris:http://127.0.0.1:9200}")
    private String uris;
    @Value("${bidding.es.username:elastic}")
    private String username;
    @Value("${bidding.es.password:}")
    private String password;
    /** ESDebug：true 时打印 ES 请求/响应摘要（本地联调用；生产关） */
    @Value("${bidding.es.debug:false}")
    private boolean debug;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1) // ES REST 走 HTTP/1.1（避免 h2c 与直连/代理差异）
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    public void indexNotice(NoticeExternalDO notice) {
        String uri = uris.replaceAll("/+$", "") + "/" + INDEX + "/_doc/" + notice.getId();
        Map<String, Object> doc = buildDoc(notice);
        try {
            String json = OBJECT_MAPPER.writeValueAsString(doc);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            if (StringUtils.hasText(password)) {
                String token = Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + token);
            }
            if (debug) {
                log.info("[EsExternalNoticeIndexNotifier][PUT {}] body={}", uri, truncate(json, 1500));
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (debug) {
                    log.info("[EsExternalNoticeIndexNotifier][PUT {}] -> {} {}", uri,
                            response.statusCode(), truncate(response.body(), 800));
                }
                return;
            }
            // 401/403（凭据/权限）与 5xx（ES 故障）同样抛错：进入旁路失败日志（无自动补偿任务，需人工复核重放）
            throw new IllegalStateException("ES 索引 HTTP " + response.statusCode()
                    + " -> " + truncate(response.body(), 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ES 索引被中断 noticeId=" + notice.getId(), e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            throw new IllegalStateException("ES 索引失败 noticeId=" + notice.getId()
                    + " uri=" + uri + ": " + e.getMessage(), e);
        }
    }

    /** 可检索字段：主列 + 正文纯文本（截断）+ 项目/标段 + 时间（索引幂等 PUT 覆盖=补偿重放安全） */
    private Map<String, Object> buildDoc(NoticeExternalDO n) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", n.getId());
        doc.put("title", n.getTitle());
        doc.put("sourceCode", n.getSourceCode());
        doc.put("contentType", n.getContentType());
        doc.put("origUrl", n.getOrigUrl());
        doc.put("issuerName", n.getIssuerName());
        doc.put("projectName", n.getProjectName());
        doc.put("projectCode", n.getProjectCode());
        doc.put("tenderProjectCode", n.getTenderProjectCode());
        doc.put("bidSectionCodes", n.getBidSectionCodes());
        doc.put("industryFlag", n.getIndustryFlag());
        doc.put("legalFlag", n.getLegalFlag());
        doc.put("status", n.getStatus());
        doc.put("publicScope", n.getPublicScope());
        doc.put("contentText", contentText(n));
        doc.put("publishTime", n.getPublishTime() == null ? null
                : ES_TIME.format(Instant.from(n.getPublishTime().atZone(ZoneId.systemDefault()))));
        return doc;
    }

    private String contentText(NoticeExternalDO n) {
        String text;
        if (StringUtils.hasText(n.getExtractionText())) {
            // PDF：OpenDataLoader 本地抽取的纯文本优先（已去噪）
            text = n.getExtractionText();
        } else if (StringUtils.hasText(n.getContentHtml())) {
            // HTML：jsoup 去标签取正文（安全净化后的可读文本）
            text = Jsoup.parse(n.getContentHtml()).text();
        } else {
            return null;
        }
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }

}
