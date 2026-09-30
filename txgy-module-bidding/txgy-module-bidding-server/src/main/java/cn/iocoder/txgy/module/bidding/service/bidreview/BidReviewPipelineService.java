package cn.iocoder.txgy.module.bidding.service.bidreview;

/**
 * 智慧标书审查 · 流水线（PARSE → RULE → GENERATE → REPORT）。
 *
 * <p>方案依据：§4.2（四步流水线）、§4.3（状态机）。
 */
public interface BidReviewPipelineService {

    /** 异步执行任务（个人端"一键生成"与管理端"重跑"都走这里）。 */
    void executeAsync(Long taskId);
}
