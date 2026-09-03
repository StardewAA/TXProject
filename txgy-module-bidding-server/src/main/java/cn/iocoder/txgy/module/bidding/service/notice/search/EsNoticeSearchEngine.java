package cn.iocoder.txgy.module.bidding.service.notice.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ES 检索实现（配置 {@code bidding.es.enabled=true} 时生效；原生 HTTP，无框架 starter 依赖）。
 *
 * <p>索引：{@code bidding_notice}（独立命名空间，不与其他系统共用）；
 * 文档含公告全文 body（仅索引不存储回显）与招标项目基础信息（title/projectCode/projectName）。
 */
@Slf4j
@Component("esNoticeSearchEngine")
@ConditionalOnProperty(prefix = "bidding.es", name = "enabled", havingValue = "true")
public class EsNoticeSearchEngine implements NoticeSearchEngine {

    private static final String INDEX = "bidding_notice";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${bidding.es.uris:http://127.0.0.1:9200}")
    private String uris;

    private volatile boolean indexEnsured;

    @Override
    public String name() {
        return "es";
    }

    @Override
    public void index(NoticeEsDoc doc) {
        ensureIndex();
        try {
            String json = MAPPER.writeValueAsString(doc);
            int code = send("PUT", "/" + INDEX + "/_doc/" + doc.getNoticeId(), json);
            if (code >= 300) {
                log.error("[index][索引失败 code={} noticeId={}]", code, doc.getNoticeId());
            }
        } catch (Exception e) {
            log.error("[index][索引异常 noticeId={}]", doc.getNoticeId(), e);
        }
    }

    @Override
    public void remove(Long noticeId) {
        ensureIndex();
        try {
            send("DELETE", "/" + INDEX + "/_doc/" + noticeId, null);
        } catch (Exception e) {
            log.error("[remove][删除索引异常 noticeId={}]", noticeId, e);
        }
    }

    @Override
    public List<Long> matchIds(String keyword, int limit) {
        ensureIndex();
        try {
            String body = "{"
                    + "\"size\":" + limit + ","
                    + "\"query\":{\"bool\":{\"must\":[{\"multi_match\":{"
                    + "\"query\":" + MAPPER.writeValueAsString(keyword)
                    + ",\"fields\":[\"title^3\",\"projectName^2\",\"projectCode\",\"body\"]"
                    + "}}]}},"
                    + "\"sort\":[{\"publishTime\":{\"order\":\"desc\"}}]"
                    + "}";
            String response = sendRaw("POST", "/" + INDEX + "/_search", body);
            JsonNode root = MAPPER.readTree(response);
            JsonNode hits = root.path("hits").path("hits");
            List<Long> ids = new ArrayList<>();
            for (JsonNode hit : hits) {
                long id = hit.path("_source").path("noticeId").asLong();
                if (id > 0) {
                    ids.add(id);
                }
            }
            return ids;
        } catch (Exception e) {
            log.error("[matchIds][检索异常 keyword={}]", keyword, e);
            return List.of();
        }
    }

    private void ensureIndex() {
        if (indexEnsured) {
            return;
        }
        try {
            // body 仅索引不存储（规范 4.6：检索取 id+摘要，详情回主表）
            String mapping = "{\"mappings\":{\"properties\":{\"body\":{\"type\":\"text\",\"store\":false}}}}";
            send("PUT", "/" + INDEX, mapping);
        } catch (Exception e) {
            log.debug("[ensureIndex][索引已存在或创建失败（忽略）] {}", e.getMessage());
        }
        indexEnsured = true;
    }

    private int send(String method, String path, String json) throws IOException {
        return sendRaw(method, path, json) == null ? -1 : 200;
    }

    private String sendRaw(String method, String path, String json) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(uris + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(5000);
            if (json != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            byte[] bytes = connection.getErrorStream() != null && code >= 400
                    ? connection.getErrorStream().readAllBytes()
                    : connection.getInputStream().readAllBytes();
            String response = new String(bytes, StandardCharsets.UTF_8);
            if (code >= 300 && !(method.equals("PUT") && code == 400)) {
                throw new IOException("ES " + method + " " + path + " -> " + code + " " + response);
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }
}
