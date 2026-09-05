package cn.iocoder.txgy.module.bidding.service.crawlexternal;
// 规范依据：设计文档 v0.6 §三/§四/§15.4（urlHash 幂等、指数退避 3 次、死信转审核、审计 message）；《招投标模块开发规范》§4.4

import cn.iocoder.txgy.module.bidding.dal.dataobject.crawlexternal.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.crawlexternal.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.CrawlTaskStatusEnum;
import cn.iocoder.txgy.module.bidding.service.crawlexternal.sink.CrawlDeadLetterSink;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 采集任务编排实现。
 */
@Slf4j
@Service
public class CrawlTaskServiceImpl implements CrawlTaskService {

    /** 最大尝试次数（设计 §三：>3 进审核 → 第 3 次失败后即死信） */
    public static final int MAX_ATTEMPTS = 3;

    /** 指数退避基数（秒）：第 n 次失败后等待 2^n * 60 秒（2 分钟/4 分钟/8 分钟） */
    private static final long BACKOFF_BASE_SECONDS = 60L;

    @Resource
    private CrawlTaskMapper crawlTaskMapper;
    @Resource
    private CrawlDeadLetterSink crawlDeadLetterSink;

    @Override
    public Long createTaskIfAbsent(Long jobId, String sourceCode, String url, String urlHash,
                                   String channelCode, String parserCode, Integer priority) {
        // urlHash 幂等去重：同 URL 未终态任务直接复用（设计 §二/§三：重复消费靠 urlHash+fetchedAt 幂等）
        CrawlTaskDO existing = crawlTaskMapper.selectOne(new LambdaQueryWrapper<CrawlTaskDO>()
                .eq(CrawlTaskDO::getUrlHash, urlHash)
                .in(CrawlTaskDO::getStatus, CrawlTaskStatusEnum.PENDING.getStatus(),
                        CrawlTaskStatusEnum.RUNNING.getStatus())
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("[createTaskIfAbsent][urlHash={}] 命中未终态任务 taskId={}，复用", urlHash, existing.getId());
            return existing.getId();
        }
        CrawlTaskDO task = new CrawlTaskDO();
        task.setJobId(jobId);
        task.setSourceCode(sourceCode);
        task.setUrl(url);
        task.setUrlHash(urlHash);
        task.setChannelCode(channelCode);
        task.setParserCode(parserCode);
        task.setPriority(priority);
        task.setStatus(CrawlTaskStatusEnum.PENDING.getStatus());
        task.setAttempt(0);
        crawlTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    public void markRunning(Long taskId, String workerId) {
        CrawlTaskDO task = require(taskId);
        task.setStatus(CrawlTaskStatusEnum.RUNNING.getStatus());
        task.setWorkerId(workerId);
        task.setStartedAt(LocalDateTime.now());
        crawlTaskMapper.updateById(task);
    }

    @Override
    public void markDownloadSuccess(Long taskId, String contentRef, String contentType) {
        CrawlTaskDO task = require(taskId);
        task.setStatus(CrawlTaskStatusEnum.SUCCESS.getStatus());
        task.setFinishedAt(LocalDateTime.now());
        task.setMessage((contentRef == null ? "" : "contentRef=" + contentRef)
                + (contentType == null ? "" : " contentType=" + contentType));
        crawlTaskMapper.updateById(task);
    }

    @Override
    public CrawlAttemptOutcome handleFailure(Long taskId, String cause) {
        CrawlTaskDO task = require(taskId);
        int attempt = (task.getAttempt() == null ? 0 : task.getAttempt()) + 1;
        task.setAttempt(attempt);
        if (attempt >= MAX_ATTEMPTS) {
            // 重试耗尽：死信 → 转人工审核（crawl.audit 主题/审核队列，见设计 §三/§15.4）
            task.setStatus(CrawlTaskStatusEnum.FAILED.getStatus());
            task.setFinishedAt(LocalDateTime.now());
            task.setMessage("DLQ>audit attempt=" + attempt + " cause=" + cause);
            crawlTaskMapper.updateById(task);
            try {
                crawlDeadLetterSink.audit(task);
            } catch (Exception e) {
                log.error("[handleFailure][taskId={}] 死信转审核出口异常（不影响状态落库）", taskId, e);
            }
            log.warn("[handleFailure][taskId={} url={}] 第 {} 次失败，重试耗尽→转人工审核", taskId, task.getUrl(), attempt);
            return CrawlAttemptOutcome.DEAD_LETTER_AUDITED;
        }
        // 未耗尽：指数退避后回 PENDING（等价 crawl.retry 重投，Redis Stream 确认/重投语义由接入的 MQ 承担）
        task.setStatus(CrawlTaskStatusEnum.PENDING.getStatus());
        task.setNextRetryTime(LocalDateTime.now().plusSeconds(backoffSeconds(attempt)));
        task.setMessage("retry scheduled attempt=" + attempt + " cause=" + cause);
        crawlTaskMapper.updateById(task);
        log.info("[handleFailure][taskId={}] 第 {} 次失败，{} 秒后重试", taskId, attempt, backoffSeconds(attempt));
        return CrawlAttemptOutcome.RETRY_SCHEDULED;
    }

    /** 指数退避：第 n 次失败后等待 2^n 分钟（n=1→2min，n=2→4min） */
    long backoffSeconds(int attempt) {
        return BACKOFF_BASE_SECONDS * (1L << attempt);
    }

    private CrawlTaskDO require(Long taskId) {
        CrawlTaskDO task = crawlTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("采集任务不存在 taskId=" + taskId);
        }
        return task;
    }

}
