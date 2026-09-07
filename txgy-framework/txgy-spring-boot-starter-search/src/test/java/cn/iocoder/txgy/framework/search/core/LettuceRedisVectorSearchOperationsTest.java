package cn.iocoder.txgy.framework.search.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LettuceRedisVectorSearchOperationsTest {

    @Test
    void parsesRedisSearchResp2Result() {
        List<Object> response = List.of(
                1L,
                bytes("txgy:cms:law:vector:1:test"),
                List.of(bytes("source_id"), bytes("article-232"), bytes("version"), bytes("v1"),
                        bytes("vector_distance"), bytes("0.125")));

        assertThat(LettuceRedisVectorSearchOperations.parseSearchResponse(response))
                .containsExactly(new VectorSearchHit("article-232", "v1", 0.125d));
    }

    @Test
    void parsesRedis8Resp3Result() {
        List<Object> response = List.of(
                bytes("attributes"), List.of(),
                bytes("format"), bytes("STRING"),
                bytes("results"), List.of(List.of(
                        bytes("id"), bytes("key"),
                        bytes("extra_attributes"), List.of(bytes("vector_distance"), bytes("0.25"),
                                bytes("source_id"), bytes("article-233"), bytes("version"), bytes("v2")),
                        bytes("values"), List.of())),
                bytes("total_results"), 1L,
                bytes("warning"), List.of());

        assertThat(LettuceRedisVectorSearchOperations.parseSearchResponse(response))
                .containsExactly(new VectorSearchHit("article-233", "v2", 0.25d));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
