package cn.iocoder.txgy.module.bidding.service.crawlexternal;
// [依据] 下载器触发实现：委托 CrawlSourceRunner；定时调度（30s tick）按源 fetchIntervalSeconds（≥120）节流轮询。

import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlSourceDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlSourceMapper;
import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlRunRespVO;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.fetch.CrawlSourceRunner;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CrawlRunServiceImpl implements CrawlRunService {

    /** 调度开关（默认关；联调用人工触发，生产开启后按源间隔轮询） */
    @Value("${bidding.crawl.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    /** 默认轮询间隔（秒）：源未配置 fetch_interval_seconds 时使用（≥120 防封） */
    private static final int DEFAULT_INTERVAL_SECONDS = 300;

    @Resource
    private CrawlSourceRunner crawlSourceRunner;
    @Resource
    private CrawlSourceMapper crawlSourceMapper;

    /** 源级上次执行时间（进程内；v1 简化，多实例见 P2 worker 归属） */
    private final ConcurrentHashMap<String, Instant> lastRunAt = new ConcurrentHashMap<>();

    @Override
    public CrawlRunRespVO runNow(String sourceCode) {
        CrawlSourceRunner.RunSummary summary = crawlSourceRunner.runSource(sourceCode);
        lastRunAt.put(sourceCode, Instant.now());
        log.info("[crawl][runNow][{}] ingested={} failed={}", sourceCode,
                summary.getTotalIngested(), summary.getTotalFailed());
        CrawlRunRespVO vo = new CrawlRunRespVO();
        vo.setSourceCode(summary.getSourceCode());
        vo.setStartedAt(summary.getStartedAt());
        vo.setFinishedAt(summary.getFinishedAt());
        vo.setTotalIngested(summary.getTotalIngested());
        vo.setTotalFailed(summary.getTotalFailed());
        if (summary.getCategories() != null) {
            java.util.List<CrawlRunRespVO.Category> list = new java.util.ArrayList<>();
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

    /** 定时扫描：启用源按各自间隔轮询（tick 30s） */
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
                    runNow(source.getSourceCode());
                } catch (Exception e) {
                    log.error("[crawl][scheduled][{}] 轮询失败", source.getSourceCode(), e);
                }
            }
        } catch (Exception e) {
            log.error("[crawl][scheduled] 调度扫描异常", e);
        }
    }

}
