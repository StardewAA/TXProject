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

    /** 源级上次执行时间（进程内；v1 简化，多实例见 P2 worker 归属） */
    private final ConcurrentHashMap<String, Instant> lastRunAt = new ConcurrentHashMap<>();

    /** 源级进程内互斥锁：同源并发 runNow（人工 run 撞上调度 tick）第二个直接跳过（DB RUNNING 守卫之外的第二道闸） */
    private final java.util.concurrent.ConcurrentHashMap<String, Object> sourceLocks = new java.util.concurrent.ConcurrentHashMap<>();

    /** 启动自愈：把上次进程崩溃遗留的 RUNNING 批次统一置 FAILED（记 interrupted），否则 RUNNING 守卫会永远跳过该源（2026-09-09 容错）
     *  首次全量判定基于 SUCCESS：清理后该源仍无 SUCCESS → 下次运行自动按 full 重试（幂等补齐，不丢历史）。 */
    @PostConstruct
    public void recoverInterruptedJobsOnStartup() {
        try {
            // 字符串 UpdateWrapper（避免单测无 mybatis-plus lambda cache 的依赖；列名驼峰→下划线）
            int n = crawlJobMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<CrawlJobDO>()
                            .eq("status", "RUNNING")
                            .set("status", "FAILED")
                            .set("finished_at", LocalDateTime.now())
                            .set("summary_json", "{\"error\":\"interrupted by restart\"}"));
            if (n > 0) {
                log.warn("[crawl][startup] 清理 {} 条崩溃遗留 RUNNING 批次 → FAILED（幂等自愈，首次全量将重试）", n);
            }
        } catch (Exception e) {
            log.error("[crawl][startup] 恢复 RUNNING 批次失败", e);
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
            log.info("[crawl][runNow][{}] ingested={} failed={}", sourceCode,
                    summary.getTotalIngested(), summary.getTotalFailed());
            finishJob(job, statusOf(summary), summaryToJson(summary));
            return toRespVO(summary);
        } catch (Exception e) {
            log.error("[crawl][runNow][{}] 执行异常，批次 {} 置 FAILED", sourceCode, job.getId(), e);
            finishJob(job, "FAILED", summaryToJson(null));
            throw e;
        }
    }

    /** 定时扫描：启用源按各自间隔轮询（tick 30s；触发类型 SCHEDULE） */
    @Scheduled(fixedDelay = 30_000)
    public void scheduledScan() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            List<CrawlSourceDO> sources = crawlSourceMapper.selectList(new LambdaQueryWrapper<CrawlSourceDO>()
                    .eq(CrawlSourceDO::getEnabled, Boolean.TRUE));
            for (CrawlSourceDO source : sources) {
                int interval = source.getFetchIntervalSeconds() == null
                        ? DEFAULT_INTERVAL_SECONDS : Math.max(source.getFetchIntervalSeconds(), 120);
                Instant last = lastRunAt.get(source.getSourceCode());
                if (last != null && Duration.between(last, Instant.now()).getSeconds() < interval) {
                    continue; // 未到期（频控）
                }
                lastRunAt.putIfAbsent(source.getSourceCode(), Instant.now());
                try {
                    runNow(source.getSourceCode(), "SCHEDULE");
                } catch (Exception e) {
                    log.error("[crawl][scheduled][{}] 轮询失败", source.getSourceCode(), e);
                }
            }
        } catch (Exception e) {
            log.error("[crawl][scheduled] 调度扫描异常", e);
        }
    }

    // ========== 私有工具 ==========

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
