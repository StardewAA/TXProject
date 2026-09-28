package cn.iocoder.txgy.module.bidding.framework.bidagent;

// [规范依据]《招投标模块开发规范》§4.2（外部调用封装放 framework/）
// [业务目标] 标书智能体检索不再自建私有化模型（TEI），统一走阿里云百炼云端 embedding
// [为什么] 云端 key/配额/模型由平台统一持有，避免"本地模型没起 → 检索静默失效"的协同成本；
//          缺 key 或调用失败必须抛可读错误（BID_AGENT_CLOUD_UNAVAILABLE），不做静默降级

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_CLOUD_UNAVAILABLE;

/**
 * 阿里云百炼（DashScope）云端 Embedding 客户端。
 *
 * <p>调用形态：OpenAI 兼容端点 {@code POST {base-url}/compatible-mode/v1/embeddings}，
 * 请求体 {@code {"model","input":[...],"dimensions":N,"encoding_format":"float"}}，
 * 鉴权 {@code Authorization: Bearer ${DASHSCOPE_API_KEY}}。
 *
 * <p>兼容两种响应体：OpenAI 兼容的 {@code data[].embedding} 与 DashScope 原生的
 * {@code output.embeddings[].embedding}（换域名/换端点时不至于解析失败）。
 */
@Component
@Slf4j
public class DashScopeEmbeddingClient {

    /** OpenAI 兼容的向量端点（百炼） */
    private static final String PATH_EMBEDDINGS = "/compatible-mode/v1/embeddings";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BidAgentProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public DashScopeEmbeddingClient(BidAgentProperties properties) {
        this.properties = properties;
    }

    /** 向量维度（与 Qdrant 集合维度、bidding.bid-agent.embedding.dimensions 一致） */
    public int dimensions() {
        Integer dimensions = properties.getEmbedding() == null ? null : properties.getEmbedding().getDimensions();
        return dimensions == null || dimensions <= 0 ? 1024 : dimensions;
    }

    /** 单条文本向量化 */
    public float[] embedOne(String text) {
        List<float[]> vectors = embed(List.of(text));
        if (vectors.isEmpty()) {
            throw exception(BID_AGENT_CLOUD_UNAVAILABLE, "embedding 返回为空");
        }
        return vectors.get(0);
    }

    /**
     * 批量向量化（按 batch-size 切片多次调用百炼，保持返回顺序与入参一致）
     */
    public List<float[]> embed(List<String> inputs) {
        List<float[]> result = new ArrayList<>();
        if (inputs == null || inputs.isEmpty()) {
            return result;
        }
        BidAgentProperties.Embedding config = properties.getEmbedding();
        int batchSize = config == null || config.getBatchSize() == null || config.getBatchSize() <= 0
                ? 10 : config.getBatchSize();
        for (int from = 0; from < inputs.size(); from += batchSize) {
            int to = Math.min(from + batchSize, inputs.size());
            result.addAll(embedBatch(inputs.subList(from, to)));
        }
        return result;
    }

    private List<float[]> embedBatch(List<String> batch) {
        BidAgentProperties.Embedding config = properties.getEmbedding();
        if (config == null || StrUtil.isBlank(config.getApiKey())) {
            throw exception(BID_AGENT_CLOUD_UNAVAILABLE,
                    "未配置百炼 key：请设置 bidding.bid-agent.embedding.api-key（环境变量 DASHSCOPE_API_KEY）");
        }
        if (StrUtil.isBlank(config.getBaseUrl())) {
            throw exception(BID_AGENT_CLOUD_UNAVAILABLE, "未配置百炼 base-url");
        }
        String uri = config.getBaseUrl().replaceAll("/+$", "") + PATH_EMBEDDINGS;
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", StrUtil.blankToDefault(config.getModel(), "text-embedding-v3"));
        body.put("dimensions", dimensions());
        body.put("encoding_format", "float");
        ArrayNode input = body.putArray("input");
        for (String text : batch) {
            input.add(StrUtil.nullToEmpty(text));
        }
        return parseResponse(uri, call(uri, body, config.getTimeoutSeconds()));
    }

    private JsonNode call(String uri, ObjectNode body, Integer timeoutSeconds) {
        BidAgentProperties.Embedding config = properties.getEmbedding();
        int timeout = timeoutSeconds == null || timeoutSeconds <= 0 ? 60 : timeoutSeconds;
        // 云端偶发网络抖动（TLS 握手中断 / 连接被重置）会直接导致材料入库失败，这里做有限重试；
        // 4xx（请求体/鉴权）重试无意义，直接失败。默认重试 3 次，间隔退避。
        final int maxAttempts = 3;
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                        .timeout(Duration.ofSeconds(timeout))
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .header("Authorization", "Bearer " + config.getApiKey().trim())
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    return MAPPER.readTree(response.body());
                }
                if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    log.error("[DashScopeEmbeddingClient][call] HTTP {} uri={} body={}", response.statusCode(), uri,
                            StrUtil.maxLength(response.body(), 400));
                    throw exception(BID_AGENT_CLOUD_UNAVAILABLE,
                            "embedding HTTP " + response.statusCode() + "：" + StrUtil.maxLength(response.body(), 200));
                }
                lastError = new java.io.IOException("HTTP " + response.statusCode());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw exception(BID_AGENT_CLOUD_UNAVAILABLE, "embedding 调用被中断");
            } catch (java.io.IOException ex) {
                lastError = ex;
            }
            if (attempt < maxAttempts) {
                log.warn("[DashScopeEmbeddingClient][call] 调用失败，第 {} 次重试 uri={} msg={}", attempt + 1, uri,
                        lastError == null ? "未知" : lastError.getMessage());
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw exception(BID_AGENT_CLOUD_UNAVAILABLE, "embedding 调用被中断");
                }
            }
        }
        log.error("[DashScopeEmbeddingClient][call] 重试后仍失败 uri={} msg={}", uri,
                lastError == null ? "未知" : lastError.getMessage());
        throw exception(BID_AGENT_CLOUD_UNAVAILABLE,
                "embedding 调用失败：" + (lastError == null ? "未知错误" : lastError.getMessage()));
    }

    private List<float[]> parseResponse(String uri, JsonNode node) {
        // 1. OpenAI 兼容：data[].embedding（带 index，按 index 排序保证顺序）
        JsonNode data = node.path("data");
        if (data.isArray() && !data.isEmpty()) {
            List<JsonNode> items = new ArrayList<>();
            data.forEach(items::add);
            items.sort(Comparator.comparingInt(item -> item.path("index").asInt(0)));
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : items) {
                vectors.add(toVector(item.path("embedding"), uri));
            }
            return vectors;
        }
        // 2. DashScope 原生：output.embeddings[].embedding（带 text_index）
        JsonNode embeddings = node.path("output").path("embeddings");
        if (embeddings.isArray() && !embeddings.isEmpty()) {
            List<JsonNode> items = new ArrayList<>();
            embeddings.forEach(items::add);
            items.sort(Comparator.comparingInt(item -> item.path("text_index").asInt(0)));
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : items) {
                vectors.add(toVector(item.path("embedding"), uri));
            }
            return vectors;
        }
        log.error("[DashScopeEmbeddingClient][parseResponse] 响应体无法识别 uri={} body={}", uri,
                StrUtil.maxLength(node.toString(), 400));
        throw exception(BID_AGENT_CLOUD_UNAVAILABLE, "embedding 响应体无法识别");
    }

    private float[] toVector(JsonNode array, String uri) {
        if (!array.isArray() || array.isEmpty()) {
            throw exception(BID_AGENT_CLOUD_UNAVAILABLE, "embedding 返回向量为空");
        }
        float[] vector = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            vector[i] = (float) array.get(i).asDouble();
        }
        if (vector.length != dimensions()) {
            // 维度不一致会让 Qdrant 写入/检索直接失败，这里提前给出可操作提示（换模型时改 dimensions 或重建集合）
            log.warn("[DashScopeEmbeddingClient][toVector] 向量维度({}) 与配置 dimensions({}) 不一致 uri={}",
                    vector.length, dimensions(), uri);
        }
        return vector;
    }
}
