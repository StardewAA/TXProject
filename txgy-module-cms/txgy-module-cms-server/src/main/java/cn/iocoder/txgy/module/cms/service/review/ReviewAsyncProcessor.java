package cn.iocoder.txgy.module.cms.service.review;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ReviewAsyncProcessor {
    private final ReviewService reviewService;

    public ReviewAsyncProcessor(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void review(ReviewModels.ReviewSubmittedEvent event) {
        try {
            reviewService.completeAiReview(event.tenantId(), event.reviewId());
        } catch (RuntimeException ex) {
            log.error("[review][异步合规初审失败，tenantId={}, reviewId={}]", event.tenantId(), event.reviewId(), ex);
        }
    }
}
