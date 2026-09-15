package cn.iocoder.txgy.framework.search.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReciprocalRankFusionTest {

    @Test
    void ranksDocumentFoundByBothEnginesFirst() {
        List<ReciprocalRankFusion.FusedHit> result = ReciprocalRankFusion.fuse(
                List.of("law-a", "law-b", "law-c"),
                List.of("law-c", "law-d", "law-a"), 60, 10);

        assertThat(result).extracting(ReciprocalRankFusion.FusedHit::sourceId)
                .containsExactly("law-a", "law-c", "law-b", "law-d");
        assertThat(result.getFirst().keywordRank()).isEqualTo(1);
        assertThat(result.getFirst().semanticRank()).isEqualTo(3);
    }
}
