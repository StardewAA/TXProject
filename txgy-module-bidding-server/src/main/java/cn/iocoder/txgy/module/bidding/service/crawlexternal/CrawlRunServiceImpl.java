package cn.iocoder.txgy.module.bidding.service.crawlexternal;
// [依据] 下载器触发实现：委托 CrawlSourceRunner；每次执行（人工/调度）落一条采集批次 bidding_crawl_job
// （RUNNING 开头 → SUCCESS/PARTIAL/FAILED 收尾 + summaryJson 汇总），失败任务经 runner 挂到该批次 jobId。
// 定时调度（30s tick）按源 fetchIntervalSeconds（≥120）节流轮询。

import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlRunRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlJobDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlJobMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.fetch.CrawlSourceRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Override
    public CrawlRunRespVO runNow(String sourceCode) {
        return runNow(sourceCode, "MANUAL");
    }

    /** 执行一轮并落批次：RUNNING 先行，成功后按汇总转 SUCCESS/PARTIAL/FAILED；异常记 FAILED 后向上抛。 */
    @Override
    public CrawlRunRespVO runNow(String sourceCode, String triggerType) {
        CrawlJobDO job = new CrawlJobDO();
        job.setSourceCode(sourceCode);
        job.setTriggerType(triggerType);
        job.setStatus("RUNNING");
        job.setFinishedAt(null);
        crawlJobMapper.insert(job);
        log.info("[crawl][runNow][{}][trigger={}] 批次 {} 开始", sourceCode, triggerType, job.getId());
        try {
            CrawlSourceRunner.RunSummary summary = crawlSourceRunner.runSource(sourceCode, job.getId());
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
