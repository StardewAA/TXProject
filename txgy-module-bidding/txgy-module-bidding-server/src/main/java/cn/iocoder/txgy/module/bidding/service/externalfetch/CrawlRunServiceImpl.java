package cn.iocoder.txgy.module.bidding.service.externalfetch;
// [依据] 下载器触发实现：委托 CrawlSourceRunner；每次执行（人工/调度）落一条采集批次 bidding_fetch_job
// （RUNNING 开头 → SUCCESS/PARTIAL/FAILED 收尾 + summaryJson 汇总），失败任务经 runner 挂到该批次 jobId。
// 定时调度（30s tick）按源 fetchIntervalSeconds（≥120）节流轮询。

import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlRunRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlJobDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlJobMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.CrawlSourceRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CrawlRunServiceImpl implements CrawlRunService {

    /** 调度开关（默认关；联调用人工触发，生产开启后按源间隔轮询） */
    @Value("${bidding.crawl.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    /** 默认轮询间隔（秒）：源未配置 fetch_interval_seconds 时使用（≥120 防封） */
    private static final int DEFAULT_INTERVAL_SECONDS = 300;

    private final ObjectMapper mapper = new ObjectMapper();

    @Resource
    private CrawlSourceRunner crawlSourceRunner;
    @Resource
    private CrawlSourceMapper crawlSourceMapper;
    @Resource
    private CrawlJobMapper crawlJobMapper;
    @Resource
    private cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper crawlTaskMapper;
    @Resource
    private cn.iocoder.txgy.framework.mq.redis.core.RedisMQTemplate redisMQTemplate;

    /** 源级上次调度尝试时刻（进程内；成功/失败都更新——节流对两者一致生效，失败不会 30s tick 反复重试） */
    private final ConcurrentHashMap<String, Instant> lastRunAt = new ConcurrentHashMap<>();

    /** 源级连续失败次数（进程内；用于失败退避：连续失败后拉长重试间隔，避免对坏源机械轰炸） */
    private final ConcurrentHashMap<String, Integer> failedStreak = new ConcurrentHashMap<>();

    /** 失败退避基数（秒）：第 n 次连续失败后等待 min(退避基数 * 2^(n-1), 上限) 再重试（job FAILED=死信语义，不机械重打） */
    private static final long FAIL_BACKOFF_BASE_SECONDS = 600;
    private static final long FAIL_BACKOFF_MAX_SECONDS = 7200; // 2h 封顶

    /** 源级进程内互斥锁：同源并发 runNow（人工 run 撞上调度 tick）第二个直接跳过（DB RUNNING 守卫之外的第二道闸） */
    private final java.util.concurrent.ConcurrentHashMap<String, Object> sourceLocks = new java.util.concurrent.ConcurrentHashMap<>();

    /** 调度执行池：每源独立并行调度（互不阻塞），各自按源配置 fetch_interval_seconds 节流；
     *  同源并发由 sourceLocks + DB RUNNING 守卫双保险拦截；跨源（含多实例不同源）并行执行（2026-09-09 用户口径）。 */
    private final java.util.concurrent.ExecutorService schedulerPool =
            java.util.concurrent.Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "crawl-scheduler");
                t.setDaemon(true);
                return t;
            });

    /** 启动自愈：把上次进程崩溃遗留的 RUNNING 批次统一置 FAILED（记 interrupted），否则 RUNNING 守卫会永远跳过该源（2026-09-09 容错）
     *  首次全量判定基于 SUCCESS：清理后该源仍无 SUCCESS → 下次运行自动按 full 重试（幂等补齐，不丢历史）。 */
    @PostConstruct
    public void recoverInterruptedJobsOnStartup() {
        // 先恢复 task（异步任务续跑），再清理无任务的孤儿 RUNNING 批次（同步遗留/生产端崩在投递前）
        recoverInterruptedTasks();
        try {
            // 有未终态 task 的 RUNNING job 保留（消费端续跑后 tryFinalizeJob 自然收尾）；仅清理无 task 的孤儿
            List<CrawlJobDO> runningJobs = crawlJobMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CrawlJobDO>()
                            .eq(CrawlJobDO::getStatus, "RUNNING"));
            if (runningJobs == null || runningJobs.isEmpty()) {
                return;
            }
            int cleaned = 0;
            for (CrawlJobDO job : runningJobs) {
                Long pendingTasks = crawlTaskMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO>()
                                .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getJobId, job.getId())
                                .in(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getStatus, "PENDING", "RUNNING"));
                if (pendingTasks != null && pendingTasks > 0) {
                    log.info("[crawl][startup][job={}] 有未终态 task（异步续跑），job 保留 RUNNING", job.getId());
                    continue;
                }
                // 孤儿批次（同步模式遗留/投递前崩溃）：置 FAILED 记 interrupted
                crawlJobMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<CrawlJobDO>()
                                .eq("id", job.getId())
                                .eq("status", "RUNNING")
                                .set("status", "FAILED")
                                .set("finished_at", LocalDateTime.now())
                                .set("summary_json", "{\"error\":\"interrupted by restart\"}"));
                cleaned++;
            }
            if (cleaned > 0) {
                log.warn("[crawl][startup] 清理 {} 条孤儿 RUNNING 批次 → FAILED（幂等自愈，首次全量将重试）", cleaned);
            }
        } catch (Exception e) {
            log.error("[crawl][startup] 恢复 RUNNING 批次失败", e);
        }
    }

    /** 异步任务自愈：上次进程崩溃遗留的 RUNNING task（消费中/worker 死亡）→ 重置 PENDING 并重发轻信号。
     *  DB task 是唯一真相：即使 Redis 全丢，任务仍在 DB，重发后消费端幂等续跑（不重排任务链表）。 */
    private void recoverInterruptedTasks() {
        try {
            List<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO> stuck = crawlTaskMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO>()
                            .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getStatus, "RUNNING"));
            if (stuck == null || stuck.isEmpty()) {
                return;
            }
            log.warn("[crawl][startup] 发现 {} 条崩溃遗留 RUNNING task → 重置 PENDING + 重发轻信号", stuck.size());
            for (cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO t : stuck) {
                try {
                    t.setStatus("PENDING");
                    t.setWorkerId(null);
                    t.setStartedAt(null);
                    crawlTaskMapper.updateById(t);
                    cn.iocoder.txgy.module.bidding.service.externalfetch.mq.CrawlNoticeFetchMessage msg =
                            new cn.iocoder.txgy.module.bidding.service.externalfetch.mq.CrawlNoticeFetchMessage();
                    msg.setTaskId(t.getId());
                    msg.setJobId(t.getJobId());
                    msg.setSourceCode(t.getSourceCode());
                    redisMQTemplate.send(msg);
                } catch (Exception e) {
                    log.error("[crawl][startup][task={}] 自愈重发失败（保留 PENDING，下轮调度/人工可补）", t.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("[crawl][startup] 恢复 RUNNING task 失败", e);
        }
    }

    @Override
    public CrawlRunRespVO runNow(String sourceCode) {
        return runNow(sourceCode, "MANUAL");
    }

    /** 执行一轮并落批次：RUNNING 先行，成功后按汇总转 SUCCESS/PARTIAL/FAILED；异常记 FAILED 后向上抛。
     *  2026-09-09 并发防重：同一源已有 RUNNING 批次则跳过本轮（DB 判断，单/多实例均生效，防同源并发重复抓取）；
     *  首次全量：该源从无 SUCCESS 历史批次（首次爬取，调度器无上次断点）→ full=true 按栏目 fullLookbackDays 回溯补历史
     *  （缺省 60 天可配）；之后（有断点）→ 增量小窗口 lookbackDays。 */
    @Override
    public CrawlRunRespVO runNow(String sourceCode, String triggerType) {
        // 源级互斥（进程内）：同一源并发 runNow（人工 run 撞调度 tick）串行化，先到先跑、后到跳过
        synchronized (sourceLocks.computeIfAbsent(sourceCode, k -> new Object())) {
            return doRunNow(sourceCode, triggerType);
        }
    }

    /** runNow 实际执行体（已持 sourceCode 进程锁）。 */
    private CrawlRunRespVO doRunNow(String sourceCode, String triggerType) {
        // ① 并发防重（DB 级，多实例通用）：同源已有 RUNNING（本实例或其它实例），跳过，避免把相同任务重复投给下载器
        Long running = crawlJobMapper.selectCount(new LambdaQueryWrapper<CrawlJobDO>()
                .eq(CrawlJobDO::getSourceCode, sourceCode).eq(CrawlJobDO::getStatus, "RUNNING"));
        if (running != null && running > 0) {
            log.warn("[crawl][runNow][{}] 已有 RUNNING 批次，本轮跳过（防同源并发重复抓取）", sourceCode);
            CrawlRunRespVO vo = new CrawlRunRespVO();
            vo.setSourceCode(sourceCode);
            vo.setStartedAt(LocalDateTime.now());
            vo.setFinishedAt(LocalDateTime.now());
            return vo;
        }
        // ② 首次全量判定：源从未有 SUCCESS 批次（无上次爬取断点）→ full=true 回溯全量窗口补历史
        Long successCount = crawlJobMapper.selectCount(new LambdaQueryWrapper<CrawlJobDO>()
                .eq(CrawlJobDO::getSourceCode, sourceCode).eq(CrawlJobDO::getStatus, "SUCCESS"));
        boolean firstFull = successCount == null || successCount == 0;
        CrawlJobDO job = new CrawlJobDO();
        job.setSourceCode(sourceCode);
        job.setTriggerType(triggerType);
        job.setStatus("RUNNING");
        job.setFinishedAt(null);
        crawlJobMapper.insert(job);
        log.info("[crawl][runNow][{}][trigger={}] 批次 {} 开始{}", sourceCode, triggerType, job.getId(),
                firstFull ? "（首次全量：无历史断点，回溯 fullLookbackDays）" : "（增量）");
        try {
            CrawlSourceRunner.RunSummary summary = crawlSourceRunner.runSource(sourceCode, job.getId(), firstFull);
            lastRunAt.put(sourceCode, Instant.now());
            failedStreak.remove(sourceCode); // 成功清零（人工 run 与调度一致）
            if (summary.isAsync()) {
                // 投递模式：本轮只做列表发现+投递；job 保持 RUNNING，task 全部终态后由消费端 tryFinalizeJob 收尾
                log.info("[crawl][runNow][{}] 投递完成 enqueued={} failed={}（消费端异步落库，job 保持 RUNNING）", sourceCode,
                        summary.getTotalIngested(), summary.getTotalFailed());
                // 标记投递完成（produced=true）：消费端 tryFinalizeJob 仅在 produced 且无未终态 task 时收尾，防止投递中提前收尾
                job.setProduced(Boolean.TRUE);
                crawlJobMapper.updateById(job);
                if (summary.getTotalIngested() == 0 && summary.getTotalFailed() == 0) {
                    // 空跑（窗口内无新条目/无可投递）：直接收尾 SUCCESS，不留 RUNNING 孤儿
                    finishJob(job, statusOf(summary), summaryToJson(summary));
                }
            } else {
                log.info("[crawl][runNow][{}] ingested={} failed={}", sourceCode,
                        summary.getTotalIngested(), summary.getTotalFailed());
                finishJob(job, statusOf(summary), summaryToJson(summary));
            }
            return toRespVO(summary);
        } catch (Exception e) {
            log.error("[crawl][runNow][{}] 执行异常，批次 {} 置 FAILED", sourceCode, job.getId(), e);
            finishJob(job, "FAILED", summaryToJson(null));
            throw e;
        }
    }

    /** 定时扫描（tick 30s）：发现"到期源"后提交线程池并行执行（每源独立、互不阻塞）。
     *  每个源按自己的 fetch_interval_seconds（源配置表可调，≥120 下限）节流；到期即投池，源 A 长跑不挡源 B。
     *  分布式：同源跨实例并发由 DB RUNNING 守卫拦截，不同源可在多实例并行；本池仅加速单实例内多源并行。 */
    @Scheduled(fixedDelay = 30_000)
    public void scheduledScan() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            List<CrawlSourceDO> sources = crawlSourceMapper.selectList(new LambdaQueryWrapper<CrawlSourceDO>()
                    .eq(CrawlSourceDO::getEnabled, Boolean.TRUE));
            for (CrawlSourceDO source : sources) {
                String code = source.getSourceCode();
                int interval = source.getFetchIntervalSeconds() == null
                        ? DEFAULT_INTERVAL_SECONDS : Math.max(source.getFetchIntervalSeconds(), 120);
                Instant last = lastRunAt.get(code);
                long waited = last == null ? Long.MAX_VALUE : Duration.between(last, Instant.now()).getSeconds();
                // 节流 = 正常间隔（成功/失败一致）；若处于失败退避期则用退避间隔（连续失败指数退避，坏源不死缠）
                int streak = failedStreak.getOrDefault(code, 0);
                long effectiveInterval = streak > 0
                        ? Math.min(FAIL_BACKOFF_BASE_SECONDS * (1L << Math.min(streak - 1, 6)), FAIL_BACKOFF_MAX_SECONDS)
                        : interval;
                if (waited < effectiveInterval) {
                    continue; // 未到期（正常间隔或失败退避期）
                }
                // 原子抢占（避免并发 tick 双跑）：last 为空 → putIfAbsent；非空 → replace(旧值校验)
                boolean claimed;
                if (last == null) {
                    claimed = lastRunAt.putIfAbsent(code, Instant.now()) == null;
                } else {
                    claimed = lastRunAt.replace(code, last, Instant.now());
                }
                if (!claimed) {
                    continue; // 并发 tick 已抢先提交
                }
                schedulerPool.submit(() -> {
                    try {
                        runNow(code, "SCHEDULE");
                        failedStreak.remove(code); // 成功清零
                    } catch (Exception e) {
                        int n = failedStreak.merge(code, 1, Integer::sum);
                        log.error("[crawl][scheduled][{}] 轮询失败（连续第 {} 次，退避 {}s 后重试，job FAILED 可人工处理）", code, n,
                                Math.min(FAIL_BACKOFF_BASE_SECONDS * (1L << Math.min(n - 1, 6)), FAIL_BACKOFF_MAX_SECONDS), e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("[crawl][scheduled] 调度扫描异常", e);
        }
    }

    // ========== 私有工具 ==========

    /**
     * 消费驱动批次收尾：每条 task 终态后调用。该 job 下无 PENDING/RUNNING（未终态）task → 汇总收尾。
     * 并发安全：仅 RUNNING 状态的 job 可收尾一次（updateById 前校验），重复调用幂等。
     */
    @Override
    public void tryFinalizeJob(Long jobId) {
        if (jobId == null) {
            return;
        }
        try {
            Long pending = crawlTaskMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO>()
                    .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getJobId, jobId)
                    .in(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getStatus, "PENDING", "RUNNING"));
            if (pending != null && pending > 0) {
                return; // 仍有未终态任务，批次继续 RUNNING
            }
            CrawlJobDO job = crawlJobMapper.selectById(jobId);
            if (job == null || !"RUNNING".equals(job.getStatus())) {
                return; // 已收尾/不存在
            }
            if (!Boolean.TRUE.equals(job.getProduced())) {
                return; // 生产端尚未投递完成：此时收尾会漏掉后续投递的 task（竞态防护 2026-09-09）
            }
            // 汇总：SUCCESS 数 / FAILED 数（SKIPPED 不计失败）
            Long ok = crawlTaskMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO>()
                    .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getJobId, jobId)
                    .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getStatus, "SUCCESS"));
            Long fail = crawlTaskMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO>()
                    .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getJobId, jobId)
                    .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO::getStatus, "FAILED"));
            long okN = ok == null ? 0 : ok, failN = fail == null ? 0 : fail;
            String status = failN > 0 ? (okN > 0 ? "PARTIAL" : "FAILED") : "SUCCESS";
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("async", Boolean.TRUE);
            root.put("totalEnqueued", okN + failN);
            root.put("totalIngested", okN);
            root.put("totalFailed", failN);
            finishJob(job, status, mapper.writeValueAsString(root));
            log.info("[crawl][tryFinalizeJob][job={}] 批次收尾 status={} ok={} fail={}", jobId, status, okN, failN);
        } catch (Exception e) {
            log.error("[crawl][tryFinalizeJob][job={}] 批次收尾失败（保留 RUNNING，可人工重跑）", jobId, e);
        }
    }

    private void finishJob(CrawlJobDO job, String status, String summaryJson) {
        try {
            job.setStatus(status);
            job.setSummaryJson(summaryJson);
            job.setFinishedAt(LocalDateTime.now());
            crawlJobMapper.updateById(job);
        } catch (Exception e) {
            log.error("[crawl][finishJob][{}] 批次收尾失败", job.getId(), e);
        }
    }

    /** 汇总状态：有失败且有成功=PARTIAL；全失败=FAILED；否则 SUCCESS（含无新数据）。 */
    private static String statusOf(CrawlSourceRunner.RunSummary summary) {
        int failed = summary.getTotalFailed();
        int ingested = summary.getTotalIngested();
        if (failed > 0) {
            return ingested > 0 ? "PARTIAL" : "FAILED";
        }
        return "SUCCESS";
    }

    private String summaryToJson(CrawlSourceRunner.RunSummary summary) {
        try {
            if (summary == null) {
                return "{\"error\":\"no summary\"}";
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("totalIngested", summary.getTotalIngested());
            root.put("totalFailed", summary.getTotalFailed());
            int fetched = 0;
            List<Map<String, Object>> cats = new ArrayList<>();
            if (summary.getCategories() != null) {
                for (CrawlSourceRunner.CategorySummary cs : summary.getCategories()) {
                    fetched += cs.getFetched();
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("name", cs.getName());
                    c.put("fetched", cs.getFetched());
                    c.put("ingested", cs.getIngested());
                    c.put("failed", cs.getFailed());
                    c.put("error", cs.getError());
                    cats.add(c);
                }
            }
            root.put("totalFetched", fetched);
            root.put("categories", cats);
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return null;
        }
    }

    private CrawlRunRespVO toRespVO(CrawlSourceRunner.RunSummary summary) {
        CrawlRunRespVO vo = new CrawlRunRespVO();
        vo.setSourceCode(summary.getSourceCode());
        vo.setStartedAt(summary.getStartedAt());
        vo.setFinishedAt(summary.getFinishedAt());
        vo.setTotalIngested(summary.getTotalIngested());
        vo.setTotalFailed(summary.getTotalFailed());
        if (summary.getCategories() != null) {
            List<CrawlRunRespVO.Category> list = new ArrayList<>();
            for (CrawlSourceRunner.CategorySummary cs : summary.getCategories()) {
                CrawlRunRespVO.Category c = new CrawlRunRespVO.Category();
                c.setName(cs.getName());
                c.setFetched(cs.getFetched());
                c.setIngested(cs.getIngested());
                c.setFailed(cs.getFailed());
                c.setError(cs.getError());
                list.add(c);
            }
            vo.setCategories(list);
        }
        return vo;
    }

}
