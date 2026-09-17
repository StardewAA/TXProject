package cn.iocoder.txgy.module.bidding.service.noticeexternal;

import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 存量项目维度回填启动执行器（2026-09-09）。
 *
 * <p>用途：一次性运维工具——把历史入库的外部公告按“标题+正文”推导回填 projectCode/projectName。
 * 默认关闭；需要执行时启动参数加 --bidding.crawl.backfill-project-on-boot=true（幂等：只补空，可安全重跑）。
 *
 * <p>依据：存量 ACTIVE 20 条中 project_name 覆盖 0、project_code 仅 4 条，导致“按项目关注/聚合”闭环断链；
 * 回填逻辑与入库增强共用 NoticeProjectFieldExtractor，保证新采与存量口径一致。
 */
@Component
@Slf4j
public class NoticeProjectBackfillRunner implements ApplicationRunner {

    @Value("${bidding.crawl.backfill-project-on-boot:false}")
    private boolean enabled;

    /** 运维用途：回填完成后退出进程（配合一次性执行 --bidding.crawl.backfill-exit-after=true；默认 false 随服务常驻） */
    @Value("${bidding.crawl.backfill-exit-after:false}")
    private boolean exitAfter;

    /** 运维用途：true=按最新规则强制重写项目名（修复首轮优先级缺陷产生的合并污染值；默认 false 只补空） */
    @Value("${bidding.crawl.backfill-force-name:false}")
    private boolean forceName;

    @Resource
    private ExternalNoticeIngestServiceImpl externalNoticeIngestService;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        log.info("[backfillProject] 启动回填开关已开启，开始执行…");
        try {
            int changed = externalNoticeIngestService.backfillProjectFieldsFromContent(forceName);
            log.info("[backfillProject] 完成，共回填 {} 条", changed);
        } catch (Exception e) {
            log.error("[backfillProject] 回填异常（不影响服务启动）", e);
        } finally {
            if (exitAfter) {
                log.info("[backfillProject] exit-after 已开启，进程退出");
                System.exit(0);
            }
        }
    }
}

