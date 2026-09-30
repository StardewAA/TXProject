package cn.iocoder.txgy.module.bidding.framework.bidagent;

// [规范依据]《招投标模块开发规范》§4.2（外部调用封装放 framework/）
// [业务目标] 检索重排统一走阿里云百炼云端 rerank（替代原 TEI bge-reranker 容器）
// [为什么] 重排失败不能让整条召回链崩掉：调用异常时退回向量排序（由调用方决定），
//          但"配置缺失/被显式关闭"必须能被上层识别（isEnabled=false）

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 阿里云百炼（DashScope）云端 Rerank 客户端。
 *
 * <p>调用形态：DashScope 原生端点 {@code POST {base-url}/api/v1/services/rerank/text-rerank/text-rerank}，
 * 请求体 {@code {"model","input":{"query","documents"},"parameters":{"return_documents":false,"top_n"}}}，
 * 鉴权 {@code Authorization: Bearer <embedding.api-key>}（与 embedding 共用同一个百炼 key）。
 *
 * <p>响应兼容两种：{@code output.results[]} 与顶层 {@code results[]}，字段取 {@code index} / {@code relevance_score}。
 */
@Component
@Slf4j
public class DashScopeRerankClient {

    private static final String PATH_RERANK = "/api/v1/services/rerank/text-rerank/text-rerank";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BidAgentProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public DashScopeRerankClient(BidAgentProperties properties) {
        this.properties = properties;
    }

    /** 是否启用重排：rerank.enabled=true 且 base-url/百炼 key 已配置 */
    public boolean isEnabled() {
        BidAgentProperties.Rerank rerank = properties.getRerank();
        if (rerank == null || !Boolean.TRUE.equals(rerank.getEnabled()) || StrUtil.isBlank(rerank.getBaseUrl())) {
            return false;
        }
        return properties.getEmbedding() != null && StrUtil.isNotBlank(properties.getEmbedding().getApiKey());
    }

    /**
     * 对候选文本重排，返回按分数降序的 (index, score)；调用失败返回空表（由调用方回退向量排序）。
     */
    public List<RerankResult> rerank(String query, List<String> documents) {
        if (!isEnabled() || CollUtil.isEmpty(documents) || StrUtil.isBlank(query)) {
            return List.of();
        }
        BidAgentProperties.Rerank rerank = properties.getRerank();
        String uri = rerank.getBaseUrl().replaceAll("/+$", "") + PATH_RERANK;
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", StrUtil.blankToDefault(rerank.getModel(), "gte-rerank-v2"));
        ObjectNode input = body.putObject("input");
        input.put("query", query);
        ArrayNode docs = input.putArray("documents");
        for (String document : documents) {
            docs.add(StrUtil.nullToEmpty(document));
        }
        ObjectNode parameters = body.putObject("parameters");
        parameters.put("return_documents", false);
        if (rerank.getTopN() != null && rerank.getTopN() > 0) {
            parameters.put("top_n", Math.min(rerank.getTopN(), documents.size()));
        }
        int timeout = rerank.getTimeoutSeconds() == null || rerank.getTimeoutSeconds() <= 0 ? 60 : rerank.getTimeoutSeconds();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("Authorization", "Bearer " + properties.getEmbedding().getApiKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("[DashScopeRerankClient][rerank] HTTP {} uri={} body={}", response.statusCode(), uri,
                        StrUtil.maxLength(response.body(), 240));
                return List.of();
            }
            return parseResults(MAPPER.readTree(response.body()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[DashScopeRerankClient][rerank] 调用被中断 uri={}", uri);
            return List.of();
        } catch (java.io.IOException ex) {
            log.warn("[DashScopeRerankClient][rerank] 调用失败 uri={} msg={}", uri, ex.getMessage());
            return List.of();
        }
    }

    private List<RerankResult> parseResults(JsonNode node) {
        JsonNode results = node.path("output").path("results");
        if (!results.isArray() || results.isEmpty()) {
            results = node.path("results");
        }
        if (!results.isArray() || results.isEmpty()) {
            log.warn("[DashScopeRerankClient][parseResults] 响应体无法识别 body={}", StrUtil.maxLength(node.toString(), 240));
            return List.of();
        }
        List<RerankResult> list = new ArrayList<>();
        for (JsonNode item : results) {
            Double score = item.hasNonNull("relevance_score") ? item.get("relevance_score").asDouble()
                    : (item.hasNonNull("score") ? item.get("score").asDouble() : null);
            list.add(new RerankResult(item.path("index").asInt(), score));
        }
        list.sort(Comparator.comparingDouble((RerankResult item) ->
                item.getScore() == null ? Double.NEGATIVE_INFINITY : item.getScore()).reversed());
        return list;
    }

    /** 重排结果：候选下标 + 相关性分数 */
    @Data
    @AllArgsConstructor
    public static class RerankResult {
        private Integer index;
        private Double score;
    }
}
