package cn.iocoder.txgy.module.bidding.service.notice.search;
// 规范依据：《招投标模块开发规范》§4.6、五章三§3；05 §2.2（失败重试3次转人工）；04

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
 * <p>索引：{@code bidding-notice}（短横线命名空间，与外部公告索引 bidding-notice-external 同风格）；
 * 文档含公告全文 body（仅索引不存储回显）与招标项目基础信息（title/projectCode/projectName）。
 */
@Slf4j
@Component("esNoticeSearchEngine")
@ConditionalOnProperty(prefix = "bidding.es", name = "enabled", havingValue = "true")
public class EsNoticeSearchEngine implements NoticeSearchEngine {

    private static final String INDEX = "bidding-notice";
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
        executeWithRetry(() -> doIndex(doc), "索引 noticeId=" + doc.getNoticeId());
    }

    @Override
    public void remove(Long noticeId) {
        executeWithRetry(() -> doRemove(noticeId), "删除索引 noticeId=" + noticeId);
    }

    /** 允许抛出异常的补偿动作（引擎 IO） */
    @FunctionalInterface
    private interface EsAction {
        void run() throws Exception;
    }

    /**
     * 同步补偿（规范 4.6 / 05 §2.2 规则）：失败重试 3 次（短退避），仍失败记 ERROR 日志；
     * 转人工路径 = 公告侧 GATHER-02 公开同步接口手动重投（engine.index 再次触发）。
     */
    private void executeWithRetry(EsAction action, String describe) {
        ensureIndex();
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                action.run();
                return;
            } catch (Exception e) {
                if (attempt < 3) {
                    try {
                        Thread.sleep(200L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    log.warn("[同步补偿][第{}次重试] {}", attempt, describe);
                } else {
                    log.error("[同步补偿][3次失败，转人工（GATHER-02 手动重同步）] {}", describe, e);
                }
            }
        }
    }

    private void doIndex(NoticeEsDoc doc) throws IOException {
        String json = MAPPER.writeValueAsString(doc);
        sendRaw("PUT", "/" + INDEX + "/_doc/" + doc.getNoticeId(), json);
    }

    private void doRemove(Long noticeId) throws IOException {
        sendRaw("DELETE", "/" + INDEX + "/_doc/" + noticeId, null);
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
        sendRaw(method, path, json);
        return 200;
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
