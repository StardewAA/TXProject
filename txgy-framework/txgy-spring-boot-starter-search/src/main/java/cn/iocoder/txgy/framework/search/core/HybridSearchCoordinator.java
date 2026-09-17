package cn.iocoder.txgy.framework.search.core;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/** 并行执行关键词与语义检索；语义支路失败时保留 Elasticsearch 关键词结果。 */
public final class HybridSearchCoordinator {

    private HybridSearchCoordinator() {
    }

    public static Result search(Supplier<List<String>> keywordSearch,
                                Supplier<List<String>> semanticSearch,
                                int rankConstant, int limit) {
        Objects.requireNonNull(keywordSearch, "keywordSearch");
        Objects.requireNonNull(semanticSearch, "semanticSearch");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<String>> keywordFuture = executor.submit(keywordSearch::get);
            Future<List<String>> semanticFuture = executor.submit(semanticSearch::get);
            List<String> keywordIds = requiredResult(keywordFuture, "Elasticsearch 关键词检索失败");
            try {
                List<String> semanticIds = semanticFuture.get();
                return new Result(ReciprocalRankFusion.fuse(keywordIds, semanticIds, rankConstant, limit), true);
            } catch (ExecutionException ex) {
                return new Result(ReciprocalRankFusion.fuse(keywordIds, List.of(), rankConstant, limit), false);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("混合检索被中断", ex);
        }
    }

    private static List<String> requiredResult(Future<List<String>> future, String message)
            throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            throw new IllegalStateException(message, ex.getCause());
        }
    }

    public record Result(List<ReciprocalRankFusion.FusedHit> hits, boolean semanticAvailable) {
        public Result {
            hits = List.copyOf(hits);
        }
    }

}
