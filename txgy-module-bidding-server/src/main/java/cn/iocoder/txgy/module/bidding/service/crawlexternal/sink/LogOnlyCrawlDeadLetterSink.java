package cn.iocoder.txgy.module.bidding.service.crawlexternal.sink;
// 规范依据：设计文档 v0.6 §三/§15.4（crawl.audit 主题占位：接入 starter-mq Redis Stream 后改为生产者投递）

import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlTaskDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 死信转审核出口占位实现：仅记录日志（topic crawl.audit 预留）。
 *
 * <p>待接入：starter-mq Redis Stream 投递 {@code crawl.audit}，消息体含 taskId/url/urlHash/retryCount/失败原因；
 * 消费者侧进人工审核队列（§15.6：UNCERTAIN/FAIL_EMPTY/3 次失败按配置进人工容量）。
 */
@Slf4j
@Component
public class LogOnlyCrawlDeadLetterSink implements CrawlDeadLetterSink {

    @Override
    public void audit(CrawlTaskDO task) {
        log.info("[audit][taskId={} url={} attempt={}] 采集重试耗尽→转人工审核（crawl.audit 事件预留，MQ 接入后投递）",
                task.getId(), task.getUrl(), task.getAttempt());
    }

}
