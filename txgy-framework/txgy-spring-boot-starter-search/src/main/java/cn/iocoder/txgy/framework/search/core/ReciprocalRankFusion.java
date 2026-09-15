package cn.iocoder.txgy.framework.search.core;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 以名次而非不同引擎的原始分数融合 BM25 与向量检索结果。 */
public final class ReciprocalRankFusion {

    private ReciprocalRankFusion() {
    }

    public static List<FusedHit> fuse(List<String> keywordIds, List<String> semanticIds,
                                      int rankConstant, int limit) {
        if (rankConstant < 1 || limit < 1) {
            throw new IllegalArgumentException("rankConstant 和 limit 必须大于 0");
        }
        Map<String, MutableScore> scores = new HashMap<>();
        add(scores, keywordIds, rankConstant, true);
        add(scores, semanticIds, rankConstant, false);
        return scores.entrySet().stream()
                .map(entry -> new FusedHit(entry.getKey(), entry.getValue().score,
                        entry.getValue().keywordRank, entry.getValue().semanticRank))
                .sorted((left, right) -> {
                    int byScore = Double.compare(right.score(), left.score());
                    return byScore != 0 ? byScore : left.sourceId().compareTo(right.sourceId());
                })
                .limit(limit)
                .toList();
    }

    private static void add(Map<String, MutableScore> scores, List<String> ids,
                            int rankConstant, boolean keyword) {
        Set<String> uniqueIds = new LinkedHashSet<>(ids);
        int rank = 0;
        for (String id : uniqueIds) {
            rank++;
            if (id == null || id.isBlank()) {
                continue;
            }
            MutableScore score = scores.computeIfAbsent(id, ignored -> new MutableScore());
            score.score += 1.0d / (rankConstant + rank);
            if (keyword) {
                score.keywordRank = rank;
            } else {
                score.semanticRank = rank;
            }
        }
    }

    public record FusedHit(String sourceId, double score, Integer keywordRank, Integer semanticRank) {
    }

    private static final class MutableScore {
        private double score;
        private Integer keywordRank;
        private Integer semanticRank;
    }

}
