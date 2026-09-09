package cn.iocoder.txgy.module.bidding.service.externalfetch.mq;

// 规范依据：外部信息获取 v1.0 §五/§六 + 2026-09-09 审查 A3/A4 修复：
//   A4：节流移出 onMessage（消息快速 ACK，不持未 ACK 长睡→杜绝 pending 重投双跑）；5 分钟/条由“执行前节流闸 + 30s 兜底扫描”保证
//   A3：失败重试只改 DB 后信号丢失→task 永久 PENDING→源停摆；新增 PENDING 到期扫描兜底重投（DB task=唯一真相，消息只是唤醒）

import cn.iocoder.txgy.framework.mq.redis.core.stream.AbstractRedisStreamMessageListener;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.CrawlTaskStatusEnum;
import cn.iocoder.txgy.module.bidding.service.externalfetch.CrawlNoticeProcessor;
import cn.iocoder.txgy.module.bidding.service.externalfetch.CrawlTaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 信息获取任务消费者：监听 Redis Stream 轻信号（CrawlNoticeFetchMessage）。
 *
 * <p>处理流（审查 A4 修复，2026-09-09）：收到 taskId → 节流闸放行才执行；执行=markRunning → processor → 回写终态 → tryFinalizeJob。
 * 消息始终快速 ACK（onMessage 不 sleep 不持锁），未 ACK 长睡导致的 pending 重投双跑被消除；
 * “每条间隔 consumer-delay-ms（默认 5 分钟）”由 executeIfDue 的节流闸保证——未到点的信号直接 ACK，任务保持 PENDING 等兜底扫描。
 *
 * <p>兜底（审查 A3 修复）：@Scheduled 每 30s 扫描 PENDING 且 nextRetryTime 到期任务 → executeIfDue 重投执行；
 * 进程崩溃/消息丢失后任务仍在 DB（唯一真相），扫描续跑不重排链表；重试退避到期即被捞起，不再永久卡 PENDING。
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
    @Resource
    private cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlJobMapper crawlJobMapper;

    /** worker 标识：本机消费节点（日志/分布式归属） */
    private final String workerId = "bidding-consumer-" + System.getProperty("user.name", "local");

    /** 下载器每条任务处理间隔（毫秒）。主控在 application.yaml bidding.crawl.runner.consumer-delay-ms，默认值仅兜底 */
    @org.springframework.beans.factory.annotation.Value("${bidding.crawl.runner.consumer-delay-ms:300000}")
    private long consumerDelayMs;

    /** 上次真正执行完成时刻（ms；节流闸依据，进程内；多实例各自节流 + DB RUNNING 守卫防同任务双跑） */
    private volatile long lastConsumeAtMs = 0L;

    public CrawlNoticeFetchMessageListener() {
        super();
    }

    @Override
    public void onMessage(CrawlNoticeFetchMessage message) {
        if (message == null || message.getTaskId() == null) {
            return; // 空消息直接 ACK
        }
        executeIfDue(message.getTaskId()); // 未到节流点则本信号直接 ACK，任务留 PENDING 等兜底扫描
    }

    /**
     * 兜底扫描（30s tick）：捞到期 PENDING 任务执行（A3：失败重试/信号丢失后不永久卡 PENDING）。
     * 条件：PENDING 且（nextRetryTime 为空=正常入队 或 nextRetryTime<=now=退避到期），按 id 升序取一条尝试。
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30_000)
    public void scanPendingAndConsume() {
        try {
            CrawlTaskDO due = crawlTaskMapper.selectOne(new LambdaQueryWrapper<CrawlTaskDO>()
                    .eq(CrawlTaskDO::getStatus, CrawlTaskStatusEnum.PENDING.getStatus())
                    .and(w -> w.isNull(CrawlTaskDO::getNextRetryTime)
                            .or().le(CrawlTaskDO::getNextRetryTime, java.time.LocalDateTime.now()))
                    .orderByAsc(CrawlTaskDO::getId)
                    .last("LIMIT 1"));
            if (due != null) {
                executeIfDue(due.getId());
            }
            // 2026-09-09 兜底收尾：produced=1 且 task 全终态但没收尾的 RUNNING job（消费后 produced 未置时 tryFinalizeJob 早退的补收）
            tryFinalizeStaleJobs();
        } catch (Exception e) {
            log.warn("[scanPendingAndConsume] 扫描异常（下轮再试）: {}", e.getMessage());
        }
    }

    /** 兜底收尾：扫 RUNNING job 且 produced=1，无 PENDING/RUNNING task 的 → tryFinalizeJob（幂等，多次调用安全） */
    private void tryFinalizeStaleJobs() {
        try {
            List<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlJobDO> running =
                    crawlJobMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlJobDO>()
                            .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlJobDO::getStatus, "RUNNING"));
            for (cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlJobDO job : running) {
                if (!Boolean.TRUE.equals(job.getProduced())) {
                    continue; // 仍在投递：不收尾
                }
                Long pending = crawlTaskMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO>()
                        .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getJobId, job.getId())
                        .in(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getStatus, "PENDING", "RUNNING"));
                if (pending == null || pending == 0) {
                    crawlRunService.tryFinalizeJob(job.getId());
                }
            }
        } catch (Exception e) {
            log.debug("[tryFinalizeStaleJobs] 兜底收尾扫描异常（下轮再试）: {}", e.getMessage());
        }
    }

    /**
     * 节流闸 + 执行一条任务。距上次执行不足 consumerDelayMs → 不执行（任务保持 PENDING，等下一信号/扫描）；
     * 到点才执行，执行完更新 lastConsumeAtMs。幂等：任务不存在/已终态直接跳过。
     */
    private void executeIfDue(Long taskId) {
        long now = System.currentTimeMillis();
        if (now - lastConsumeAtMs < consumerDelayMs) {
            return; // 节流未到点：本信号/扫描不作为（不持锁不 sleep，快速返回）
        }
        CrawlTaskDO task = crawlTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        if (isTerminal(task.getStatus())) {
            return; // 已终态：幂等跳过（含 pending 重投到已 SUCCESS 的消息）
        }
        // 抢占：仅 PENDING 可执行；RUNNING（他实例/上次未收尾）不动（DB RUNNING 守卫防双跑）
        if (!CrawlTaskStatusEnum.PENDING.getStatus().equals(task.getStatus())) {
            return;
        }
        lastConsumeAtMs = now; // 到点，占住节流位
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
        }
        // 消费驱动批次收尾：终态后检查 job 是否可收尾（幂等）
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