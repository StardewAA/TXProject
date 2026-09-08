package cn.iocoder.txgy.module.bidding.service.externalfetch.mq;

// 规范依据：外部信息获取 v1.0 §五/§六（Redis Stream 轻信号消费者：收 taskId → markRunning → processor 执行 → 回写终态；
//           异常不向上抛（业务自管退避重试，框架 pending 重投仅兜进程崩溃未 ACK）；幂等：task 已终态直接 ACK）。

import cn.iocoder.txgy.framework.mq.redis.core.stream.AbstractRedisStreamMessageListener;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.CrawlTaskStatusEnum;
import cn.iocoder.txgy.module.bidding.service.externalfetch.CrawlNoticeProcessor;
import cn.iocoder.txgy.module.bidding.service.externalfetch.CrawlTaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 信息获取任务消费者：监听 Redis Stream 轻信号（CrawlNoticeFetchMessage），逐条执行下载/落库。
 *
 * <p>处理流：收到 taskId → 回查 task（不存在/已终态=幂等 ACK）→ markRunning(workerId) → 
 * CrawlNoticeProcessor.execute（按 payload/rawFileUrl 取原文落库）→ 成功 markDownloadSuccess；
 * 失败 handleFailure（attempt&lt;3 退避重试 / =3 死信转审核）。业务异常不向上抛（避免框架 pending 无限重投）；
 * 进程崩溃未 ACK 的消息由 RedisPendingMessageResendJob 5 分钟超时重投（重启不丢任务）。
 */
@Slf4j
@Component
public class CrawlNoticeFetchMessageListener extends AbstractRedisStreamMessageListener<CrawlNoticeFetchMessage> {

    @Resource
    private CrawlTaskMapper crawlTaskMapper;
    @Resource
    private CrawlTaskService crawlTaskService;
    @Resource
    private CrawlNoticeProcessor crawlNoticeProcessor;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.CrawlRunService crawlRunService;

    /** worker 标识：本机消费节点（日志/分布式归属） */
    private final String workerId = "bidding-consumer-" + System.getProperty("user.name", "local");

    public CrawlNoticeFetchMessageListener() {
        super();
    }

    @Override
    public void onMessage(CrawlNoticeFetchMessage message) {
        if (message == null || message.getTaskId() == null) {
            return; // 空消息直接 ACK
        }
        Long taskId = message.getTaskId();
        CrawlTaskDO task = crawlTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[consume][taskId={}] 任务不存在（已删/回滚？）直接 ACK", taskId);
            return;
        }
        if (isTerminal(task.getStatus())) {
            log.info("[consume][taskId={} status={}] 已终态，幂等跳过 ACK", taskId, task.getStatus());
            return;
        }
        boolean finalized = false;
        try {
            crawlTaskService.markRunning(taskId, workerId);
            Long noticeId = crawlNoticeProcessor.execute(task);
            crawlTaskService.markDownloadSuccess(taskId, noticeId == null ? null : String.valueOf(noticeId), "HTML");
            log.info("[consume][taskId={} url={}] 执行成功 noticeId={}", taskId, task.getUrl(), noticeId);
        } catch (Exception e) {
            String cause = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[consume][taskId={}] 执行失败: {}", taskId, cause);
            try {
                crawlTaskService.handleFailure(taskId, cause);
            } catch (Exception ex) {
                log.error("[consume][taskId={}] 失败状态回写异常（保留原状待重投）: {}", taskId, ex.getMessage());
                finalized = true; // 状态未回写：不触发批次收尾（task 仍 RUNNING）
            }
            // 不向上抛：业务已自管退避/死信；ACK 本消息避免框架无限重投同一失败任务
        }
        // 消费驱动批次收尾：无论成败，终态后检查 job 是否可收尾（幂等；RUNNING 任务仍在队则不收）
        if (!finalized && task.getJobId() != null) {
            try {
                crawlRunService.tryFinalizeJob(task.getJobId());
            } catch (Exception e) {
                log.warn("[consume][taskId={}] 批次收尾检查异常（下次终态再试）: {}", taskId, e.getMessage());
            }
        }
    }

    private boolean isTerminal(String status) {
        if (status == null) {
            return false;
        }
        return CrawlTaskStatusEnum.SUCCESS.getStatus().equals(status)
                || CrawlTaskStatusEnum.FAILED.getStatus().equals(status)
                || CrawlTaskStatusEnum.SKIPPED.getStatus().equals(status);
    }
}