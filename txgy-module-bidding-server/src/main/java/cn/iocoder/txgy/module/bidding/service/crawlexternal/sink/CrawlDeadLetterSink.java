package cn.iocoder.txgy.module.bidding.service.crawlexternal.sink;
// 规范依据：设计文档 v0.6 §三/§15.4（死信转审核：crawl.audit 主题，重试>3 进人工/审核队列）

import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlTaskDO;

/**
 * 死信转审核出口（消息通道占位）。
 *
 * <p>设计：MQ（starter-mq Redis Stream）重试 3 次仍失败 → 死信进 {@code crawl.audit} 主题 → 人工审核队列。
 * starter-mq 依赖接入后，由 MQ 生产/消费替换本出口实现；当前以同步回调+日志占位，保证语义（重试上限→转审核）先落地可测。
 */
public interface CrawlDeadLetterSink {

    /** 将一条重试耗尽的采集任务转人工审核 */
    void audit(CrawlTaskDO task);

}
