package cn.iocoder.txgy.framework.search.core;

import cn.iocoder.txgy.framework.search.config.RedisSearchProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.IntegerOutput;
import io.lettuce.core.output.NestedMultiOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 使用 Lettuce 原生命令执行 Redis Search，支持 Redis 8 的 RESP3 响应。 */
public final class LettuceRedisVectorSearchOperations implements RedisVectorSearchOperations, AutoCloseable {

    private static final Pattern COLLECTION_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final byte[] FIELD_TENANT_ID = bytes("tenant_id");
    private static final byte[] FIELD_SOURCE_ID = bytes("source_id");
    private static final byte[] FIELD_VERSION = bytes("version");
    private static final byte[] FIELD_EMBEDDING = bytes("embedding");

    private final RedisSearchProperties properties;
    private final RedisClient client;
    private volatile StatefulRedisConnection<byte[], byte[]> connection;
    private volatile RedisCommands<byte[], byte[]> commands;

    public LettuceRedisVectorSearchOperations(RedisSearchProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        RedisURI.Builder uri = RedisURI.Builder.redis(properties.getHost(), properties.getPort())
                .withSsl(properties.isSsl())
                .withTimeout(Duration.ofMillis(properties.getTimeoutMillis()));
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            uri.withAuthentication(properties.getUsername(), Objects.requireNonNullElse(properties.getPassword(), ""));
        } else if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            uri.withPassword(properties.getPassword());
        }
        this.client = RedisClient.create(uri.build());
    }

    @Override
    public void ensureIndex(String collection) {
        String indexName = indexName(collection);
        try {
            commands().dispatch(SearchCommand.FT_INFO, new NestedMultiOutput<>(ByteArrayCodec.INSTANCE),
                    new CommandArgs<>(ByteArrayCodec.INSTANCE).add(indexName));
            return;
        } catch (RuntimeException ex) {
            if (!isMissingIndex(ex)) {
                throw ex;
            }
        }
        CommandArgs<byte[], byte[]> args = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                .add(indexName).add("ON").add("HASH").add("PREFIX").add(1).add(keyPrefix(collection))
                .add("SCHEMA").add(FIELD_TENANT_ID).add("TAG").add(FIELD_SOURCE_ID).add("TAG")
                .add(FIELD_VERSION).add("TAG").add(FIELD_EMBEDDING).add("VECTOR").add("HNSW").add(10)
                .add("TYPE").add("FLOAT32").add("DIM").add(properties.getVectorDimensions())
                .add("DISTANCE_METRIC").add(properties.getDistanceMetric()).add("M").add(16)
                .add("EF_CONSTRUCTION").add(200);
        try {
            commands().dispatch(SearchCommand.FT_CREATE, new StatusOutput<>(ByteArrayCodec.INSTANCE), args);
        } catch (RuntimeException ex) {
            if (!isExistingIndex(ex)) {
                throw ex;
            }
        }
    }

    @Override
    public void upsert(String collection, RedisVectorDocument document) {
        validateCollection(collection);
        validateVector(document.vector());
        CommandArgs<byte[], byte[]> args = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                .addKey(bytes(documentKey(collection, document.tenantId(), document.sourceId())))
                .add(FIELD_TENANT_ID).add(bytes(document.tenantId()))
                .add(FIELD_SOURCE_ID).add(bytes(document.sourceId()))
                .add(FIELD_VERSION).add(bytes(document.version()))
                .add(FIELD_EMBEDDING).add(Float32VectorCodec.encode(document.vector()));
        commands().dispatch(SearchCommand.HSET, new IntegerOutput<>(ByteArrayCodec.INSTANCE), args);
    }

    @Override
    public List<VectorSearchHit> search(String collection, long tenantId, float[] queryVector, int topK) {
        validateCollection(collection);
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId 必须大于 0");
        }
        validateVector(queryVector);
        if (topK < 1 || topK > properties.getDefaultTopK()) {
            throw new IllegalArgumentException("topK 必须在 1 到 defaultTopK 之间");
        }
        String query = "(@tenant_id:{" + tenantId + "})=>[KNN " + topK
                + " @embedding $vector AS vector_distance]";
        CommandArgs<byte[], byte[]> args = new CommandArgs<>(ByteArrayCodec.INSTANCE)
                .add(indexName(collection)).add(query)
                .add("PARAMS").add(2).add("vector").add(Float32VectorCodec.encode(queryVector))
                .add("SORTBY").add("vector_distance").add("ASC")
                .add("LIMIT").add(0).add(topK).add("RETURN").add(3)
                .add(FIELD_SOURCE_ID).add(FIELD_VERSION).add("vector_distance").add("DIALECT").add(2);
        Object response = commands().dispatch(SearchCommand.FT_SEARCH,
                new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), args);
        return parseSearchResponse(response);
    }

    @Override
    public void delete(String collection, long tenantId, String sourceId) {
        validateCollection(collection);
        if (tenantId <= 0 || sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("tenantId 和 sourceId 无效");
        }
        commands().del(bytes(documentKey(collection, tenantId, sourceId)));
    }

    static List<VectorSearchHit> parseSearchResponse(Object response) {
        if (!(response instanceof List<?> values)) {
            return List.of();
        }
        Object resp3Results = valueForKey(values, "results");
        if (resp3Results instanceof List<?> results) {
            return parseResp3Results(results);
        }
        return parseResp2Results(values);
    }

    private static List<VectorSearchHit> parseResp2Results(List<?> values) {
        if (values.size() < 3) {
            return List.of();
        }
        List<VectorSearchHit> hits = new ArrayList<>();
        for (int i = 1; i + 1 < values.size(); i += 2) {
            Object rawFields = values.get(i + 1);
            if (!(rawFields instanceof List<?> fields)) {
                continue;
            }
            VectorSearchHit hit = parseFields(fields);
            if (hit != null) {
                hits.add(hit);
            }
        }
        return List.copyOf(hits);
    }

    private static List<VectorSearchHit> parseResp3Results(List<?> results) {
        List<VectorSearchHit> hits = new ArrayList<>();
        for (Object result : results) {
            if (!(result instanceof List<?> resultFields)) {
                continue;
            }
            Object rawAttributes = valueForKey(resultFields, "extra_attributes");
            if (rawAttributes instanceof List<?> attributes) {
                VectorSearchHit hit = parseFields(attributes);
                if (hit != null) {
                    hits.add(hit);
                }
            }
        }
        return List.copyOf(hits);
    }

    private static VectorSearchHit parseFields(List<?> fields) {
        String sourceId = null;
        String version = null;
        double distance = Double.NaN;
        for (int fieldIndex = 0; fieldIndex + 1 < fields.size(); fieldIndex += 2) {
            String name = string(fields.get(fieldIndex));
            String value = string(fields.get(fieldIndex + 1));
            if ("source_id".equals(name)) {
                sourceId = value;
            } else if ("version".equals(name)) {
                version = value;
            } else if ("vector_distance".equals(name)) {
                distance = Double.parseDouble(value);
            }
        }
        return sourceId != null && version != null && Double.isFinite(distance)
                ? new VectorSearchHit(sourceId, version, distance) : null;
    }

    private static Object valueForKey(List<?> values, String key) {
        for (int i = 0; i + 1 < values.size(); i += 2) {
            if (key.equals(string(values.get(i)))) {
                return values.get(i + 1);
            }
        }
        return null;
    }

    private void validateVector(float[] vector) {
        Objects.requireNonNull(vector, "vector");
        if (vector.length != properties.getVectorDimensions()) {
            throw new IllegalArgumentException("向量维度必须为 " + properties.getVectorDimensions());
        }
    }

    private String indexName(String collection) {
        validateCollection(collection);
        return properties.getIndexPrefix() + ":" + collection + ":vector-idx-v1";
    }

    private String keyPrefix(String collection) {
        validateCollection(collection);
        return properties.getIndexPrefix() + ":" + collection + ":vector:";
    }

    private String documentKey(String collection, long tenantId, String sourceId) {
        String encodedSourceId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sourceId.getBytes(StandardCharsets.UTF_8));
        return keyPrefix(collection) + tenantId + ":" + encodedSourceId;
    }

    private static void validateCollection(String collection) {
        if (collection == null || !COLLECTION_PATTERN.matcher(collection).matches()) {
            throw new IllegalArgumentException("collection 只能包含字母、数字、下划线和连字符");
        }
    }

    private static boolean isMissingIndex(RuntimeException ex) {
        return exceptionMessageContains(ex, "unknown index") || exceptionMessageContains(ex, "no such index");
    }

    private static boolean isExistingIndex(RuntimeException ex) {
        return exceptionMessageContains(ex, "index already exists");
    }

    private static boolean exceptionMessageContains(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            if (String.valueOf(current.getMessage()).toLowerCase(Locale.ROOT).contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static byte[] bytes(Object value) {
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    private static String string(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    @Override
    public void close() {
        StatefulRedisConnection<byte[], byte[]> currentConnection = connection;
        if (currentConnection != null) {
            currentConnection.close();
        }
        client.shutdown();
    }

    private RedisCommands<byte[], byte[]> commands() {
        RedisCommands<byte[], byte[]> currentCommands = commands;
        if (currentCommands != null) {
            return currentCommands;
        }
        synchronized (this) {
            if (commands == null) {
                connection = client.connect(ByteArrayCodec.INSTANCE);
                commands = connection.sync();
            }
            return commands;
        }
    }

    private enum SearchCommand implements ProtocolKeyword {
        FT_INFO("FT.INFO"),
        FT_CREATE("FT.CREATE"),
        FT_SEARCH("FT.SEARCH"),
        HSET("HSET");

        private final byte[] command;

        SearchCommand(String command) {
            this.command = bytes(command);
        }

        @Override
        public byte[] getBytes() {
            return command;
        }
    }

}
