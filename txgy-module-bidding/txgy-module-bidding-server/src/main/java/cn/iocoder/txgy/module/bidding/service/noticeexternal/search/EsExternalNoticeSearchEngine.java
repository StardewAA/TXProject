package cn.iocoder.txgy.module.bidding.service.noticeexternal.search;
// 规范依据：设计文档 v0.6 §15.3/05 §2（外部公告索引 bidding-notice-external 建/查同源）；《招投标模块开发规范》§4.6

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 外部公告关键词检索（配置 {@code bidding.es.enabled=true} 时生效；原生 HTTP，无框架 starter 依赖）。
 *
 * <p>索引：与索引通知器 {@code EsExternalNoticeIndexNotifier} 共用
 * <code>bidding-notice-external</code>（审核通过 ACTIVE 时写入，幂等 PUT 覆盖）。
 * 检索命中 id 集合后回主表过滤 ACTIVE/SOCIAL 并分页（索引侧不存状态快照即可靠性）。
 */
@Slf4j
@Component("esExternalNoticeSearchEngine")
@ConditionalOnProperty(prefix = "bidding.es", name = "enabled", havingValue = "true")
public class EsExternalNoticeSearchEngine implements ExternalNoticeSearchEngine {

    /** 索引名：与 EsExternalNoticeIndexNotifier.INDEX 同源（短横线风格 bidding-notice-external） */
    static final String INDEX = "bidding-notice-external";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${bidding.es.uris:http://127.0.0.1:9200}")
    private String uris;
    @Value("${bidding.es.username:elastic}")
    private String username;
    @Value("${bidding.es.password:}")
    private String password;

    @Override
    public String name() {
        return "es";
    }

    @Override
    public List<Long> matchNoticeIds(String keyword, int limit) {
        try {
            String body = "{"
                    + "\"size\":" + limit + ","
                    + "\"query\":{\"bool\":{\"must\":[{\"multi_match\":{"
                    + "\"query\":" + MAPPER.writeValueAsString(keyword)
                    + ",\"fields\":[\"title^3\",\"projectName^2\",\"projectCode\",\"tenderProjectCode\",\"issuerName\",\"contentText\"]"
                    + "}}]}},"
                    + "\"sort\":[{\"publishTime\":{\"order\":\"desc\"}}]"
                    + "}";
            String response = sendRaw("POST", "/" + INDEX + "/_search", body);
            JsonNode root = MAPPER.readTree(response);
            JsonNode hits = root.path("hits").path("hits");
            List<Long> ids = new ArrayList<>();
            for (JsonNode hit : hits) {
                long id = hit.path("_source").path("id").asLong();
                if (id > 0) {
                    ids.add(id);
                }
            }
            return ids;
        } catch (Exception e) {
            // 检索侧异常不把列表页打挂：记录后返回空集（与站内引擎一致）
            log.error("[matchNoticeIds][检索异常 keyword={}]", keyword, e);
            return List.of();
        }
    }

    private String sendRaw(String method, String path, String json) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(uris + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            if (StringUtils.hasText(password)) {
                // 带认证（xpack 安全）；未配密码（无安全 ES）时不带 Authorization 头
                String auth = username + ":" + password;
                connection.setRequestProperty("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
            }
            if (json != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            java.io.InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

}
