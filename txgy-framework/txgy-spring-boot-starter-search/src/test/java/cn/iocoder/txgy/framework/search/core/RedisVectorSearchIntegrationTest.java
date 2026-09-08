package cn.iocoder.txgy.framework.search.core;

import cn.iocoder.txgy.framework.search.config.RedisSearchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "txgy.test.redis-vector", matches = "true")
class RedisVectorSearchIntegrationTest {

    @Test
    void createsHnswIndexAndKeepsTenantResultsIsolated() throws InterruptedException {
        RedisSearchProperties properties = new RedisSearchProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(6380);
        properties.setVectorDimensions(3);
        properties.setDefaultTopK(3);
        properties.setIndexPrefix("txgy:cms:integration");

        try (LettuceRedisVectorSearchOperations operations = new LettuceRedisVectorSearchOperations(properties)) {
            operations.ensureIndex("law");
            operations.upsert("law", new RedisVectorDocument(1, "article-a", "v1", new float[]{1, 0, 0}));
            operations.upsert("law", new RedisVectorDocument(1, "article-b", "v1", new float[]{0, 1, 0}));
            operations.upsert("law", new RedisVectorDocument(2, "foreign", "v1", new float[]{1, 0, 0}));

            List<VectorSearchHit> hits = awaitSearch(operations);
            assertThat(hits).extracting(VectorSearchHit::sourceId)
                    .containsExactly("article-a", "article-b")
                    .doesNotContain("foreign");

            operations.delete("law", 1, "article-a");
            operations.delete("law", 1, "article-b");
            operations.delete("law", 2, "foreign");
        }
    }

    private static List<VectorSearchHit> awaitSearch(RedisVectorSearchOperations operations)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
        List<VectorSearchHit> hits;
        do {
            hits = operations.search("law", 1, new float[]{1, 0, 0}, 3);
            if (hits.size() == 2) {
                return hits;
            }
            Thread.sleep(50);
        } while (Instant.now().isBefore(deadline));
        return hits;
    }
}
