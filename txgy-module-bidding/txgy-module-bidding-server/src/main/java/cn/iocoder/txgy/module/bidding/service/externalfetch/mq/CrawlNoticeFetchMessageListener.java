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
 * <p><b>2026-09-10 重构（合规限速）</b>：消息不再"谁的消息谁执行"，而是**纯唤醒信号**——
 * 收到消息与 30s 兜底扫描都走同一入口 {@link #consumeOldestDue()}，按 id 升序取"最老的到期 PENDING 任务"执行，
 * 保证①排队顺序②排队中的任务不会绕过队列直接触发下载。
 *
 * <p>限速位置也被纠正：间隔**只在真正下载公告原文文件那一刻**由 {@code CrawlDownloadGate} 原子占位消耗
 * （在 CrawlNoticeProcessor 内、任何 HTTP 下载之前），而不是在"任务被调度"时消耗——
 * 后者会给纯本地处理任务白扣间隔，却对下载动作本身失去约束力。未获闸位时抛
 * {@code CrawlDownloadNotDueException}，本监听器把任务放回 PENDING（next_retry_time=可下载时刻，
 * **不计**尝试次数），到点自动续跑。
 *
 * <p>并发安全：同一任务的重复下载由 {@code tryMarkRunning} 条件更新（PENDING→RUNNING）拦住；
 * 跨实例的执行权由 DB 行锁保证，限速闸同样在 DB 层原子占位（重启/多实例均不会放宽速率）。
 *
 * <p>兜底（审查 A3 修复）：@Scheduled 每 30s 扫描 PENDING 且 nextRetryTime 到期任务 → 同一入口消费；
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

    /**
     * 单实例执行互斥：防 30s 扫描与消息回调在同一 JVM 内并发进入消费（跨实例由 DB 条件抢占保证）。
     *
     * <p>为什么删掉原来的 lastConsumeAtMs：进程内变量重启即归零（重启后立刻下载）、多实例各自计时（速率 ×N），
     * 且它限制的是"任务调度"而非"下载动作"。限速职责已全部移交 CrawlDownloadGate（库内原子占位）。
     */
    private final java.util.concurrent.atomic.AtomicBoolean consuming =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public CrawlNoticeFetchMessageListener() {
        super();
    }

    @Override
    public void onMessage(CrawlNoticeFetchMessage message) {
        if (message == null || message.getTaskId() == null) {
            return; // 空消息直接 ACK
        }
        // 纯唤醒：不直接执行"消息携带的那个任务"，而是按序取最老到期任务（未到下载间隔则任务留 PENDING 排队）
        consumeOldestDue();
    }

    /**
     * 兜底扫描（30s tick）：捞到期 PENDING 任务执行（A3：失败重试/信号丢失后不永久卡 PENDING）。
     * 条件：PENDING 且（nextRetryTime 为空=正常入队 或 nextRetryTime<=now=退避到期），按 id 升序取一条尝试。
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30_000)
    public void scanPendingAndConsume() {
        try {
            consumeOldestDue();
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
     * 消费入口（消息唤醒与 30s 扫描共用）：按 id 升序取**最老的到期 PENDING 任务**执行。
     *
     * <p>为什么不用"消息里带的 taskId"：那会让新入队任务越过队列先下载（乱序），
     * 与用户口径「排队中的任务绝对不允许直接触发下载，必须按顺序流转」冲突。
     * 本方法每轮只处理一条（与"每 interval 一次原文下载"的合规节奏一致），处理完等下一个信号/扫描。
     */
    private void consumeOldestDue() {
        if (!consuming.compareAndSet(false, true)) {
            return; // 本实例上一轮尚未结束：本次信号/扫描直接返回（下一轮再试）
        }
        try {
            CrawlTaskDO due = crawlTaskMapper.selectOne(new LambdaQueryWrapper<CrawlTaskDO>()
                    .eq(CrawlTaskDO::getStatus, CrawlTaskStatusEnum.PENDING.getStatus())
                    .and(w -> w.isNull(CrawlTaskDO::getNextRetryTime)
                            .or().le(CrawlTaskDO::getNextRetryTime, java.time.LocalDateTime.now()))
                    .orderByAsc(CrawlTaskDO::getId)
                    .last("LIMIT 1"));
            if (due == null) {
                return; // 队列空/都在等待期
            }
            executeTask(due);
        } finally {
            consuming.set(false);
        }
    }

    /**
     * 执行一条已选定的任务：条件抢占 → processor（内部在下载原文前过合规限速闸）→ 回写终态。
     *
     * <p>三分支语义（务必区分）：
     * <ul>
     *   <li>抢占失败 → 放弃（他实例已执行/已终态），不下载；</li>
     *   <li>{@link cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadNotDueException}
     *       → 未到下载间隔：任务回 PENDING 排队，next_retry_time=可下载时刻，<b>不计</b>失败次数；</li>
     *   <li>其它异常 → 既有失败语义（3 次内退避重试，耗尽转人工审核）。</li>
     * </ul>
     */
    private void executeTask(CrawlTaskDO task) {
        Long taskId = task.getId();
        // 抢占执行权：条件更新（仅 PENDING → RUNNING），杜绝同一任务被并发执行导致重复下载目标站点
        if (!crawlTaskService.tryMarkRunning(taskId, workerId)) {
            return;
        }
        boolean finalized = false;
        try {
            Long noticeId = crawlNoticeProcessor.execute(task);
            crawlTaskService.markDownloadSuccess(taskId, noticeId == null ? null : String.valueOf(noticeId), "HTML");
            log.info("[consume][taskId={} url={}] 执行成功 noticeId={}", taskId, task.getUrl(), noticeId);
        } catch (cn.iocoder.txgy.module.bidding.service.externalfetch.throttle.CrawlDownloadNotDueException e) {
            // 未到合规下载间隔：不是失败 → 回 PENDING 排队（保留下载资格，到点自动续跑）
            try {
                // dueAt 为空仅在闸位行被并发删除等极端情形出现：退避 1 分钟再排队（绝不无间隔重试）
                java.time.LocalDateTime dueAt = e.getDueAt() == null
                        ? java.time.LocalDateTime.now().plusMinutes(1) : e.getDueAt();
                crawlTaskService.releaseToPending(taskId, dueAt, "等待原文下载间隔（合规限速）");
            } catch (Exception ex) {
                log.error("[consume][taskId={}] 回 PENDING 失败（保持 RUNNING，待人工/下一轮）: {}", taskId, ex.getMessage());
                finalized = true;
            }
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

}