package cn.iocoder.txgy.framework.search.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridSearchCoordinatorTest {

    @Test
    void fusesBothBranches() {
        HybridSearchCoordinator.Result result = HybridSearchCoordinator.search(
                () -> List.of("a", "b"), () -> List.of("b", "c"), 60, 10);

        assertThat(result.semanticAvailable()).isTrue();
        assertThat(result.hits()).extracting(ReciprocalRankFusion.FusedHit::sourceId)
                .containsExactly("b", "a", "c");
    }

    @Test
    void keepsKeywordResultsWhenSemanticBranchFails() {
        HybridSearchCoordinator.Result result = HybridSearchCoordinator.search(
                () -> List.of("a", "b"), () -> { throw new IllegalStateException("redis down"); }, 60, 10);

        assertThat(result.semanticAvailable()).isFalse();
        assertThat(result.hits()).extracting(ReciprocalRankFusion.FusedHit::sourceId)
                .containsExactly("a", "b");
    }
}
