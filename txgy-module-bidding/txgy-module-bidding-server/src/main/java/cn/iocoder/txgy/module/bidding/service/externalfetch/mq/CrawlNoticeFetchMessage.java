package cn.iocoder.txgy.module.bidding.service.externalfetch.mq;

// 规范依据：设计 v0.6 §三（任务异步化）+ 2026-09-10 用户口径：MySQL bidding_fetch_task=持久队列（唯一真相），
//           Redis Stream=轻信号（仅 taskId/jobId，几百字节；全文 HTML 存 task.payload_json 不进 Redis，
//           队列长存不撑 Redis；Redis 全丢由启动自愈扫 PENDING 重建消息，任务不丢）。

import cn.iocoder.txgy.framework.mq.redis.core.stream.AbstractRedisStreamMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 外部公告抓取任务消息（轻信号：指向 bidding_fetch_task 持久行，消费端回查取 payload 执行）。
 *
 * <p>消费语义：ACK 前不丢；未 ACK 由 RedisPendingMessageResendJob 5min 超时重投；
 * 消费端幂等（task 状态机 + ingest origUrlHash），重复投递安全。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlNoticeFetchMessage extends AbstractRedisStreamMessage {

    /** 任务 id（bidding_fetch_task.id；消费端回查取 payload_json + url 执行） */
    private Long taskId;

    /** 采集批次 id（冗余：消息侧便于观测/过滤；权威以 task.job_id 为准） */
    private Long jobId;

    /** 采集源 code（冗余观测） */
    private String sourceCode;

}
