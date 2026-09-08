package cn.iocoder.txgy.module.bidding.service.externalfetch;
// 规范依据：设计文档 v0.6 §三/§四/§15.4（urlHash 幂等、指数退避 3 次、死信转审核、审计 message）；《招投标模块开发规范》§4.4

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlTaskRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.CrawlTaskStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.externalfetch.bloom.UrlBloomFilter;
import cn.iocoder.txgy.module.bidding.service.externalfetch.sink.CrawlDeadLetterSink;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    /** 布隆判重前置缓存（可选：容器内无该 Bean/未接入时走 DB 全量复核） */
    @Resource
    private UrlBloomFilter urlBloomFilter;

    @Override
    public Long createTaskIfAbsent(Long jobId, String sourceCode, String url, String urlHash,
                                   String channelCode, String parserCode, Integer priority) {
        // jobId 归一：null（无批次/人工单抓）→ 0L，保证唯一键 uk_task_job_url 全维度覆盖（MySQL 唯一键对 NULL 不生效）
        Long normalizedJob = jobId == null ? 0L : jobId;
        // 布隆判重短路：未见过（假阴性=0）→ 免 DB 查询直插；撞唯一键（重启/多节点竞态）回退 DB 复核（正确性不丢）
        if (urlBloomFilter != null && !urlBloomFilter.mightContain(urlHash)) {
            try {
                CrawlTaskDO task = newTask(normalizedJob, sourceCode, url, urlHash, channelCode, parserCode, priority);
                crawlTaskMapper.insert(task);
                urlBloomFilter.mark(urlHash);
                log.info("[createTaskIfAbsent][urlHash={}] 布隆未命中→直插新任务 taskId={}", urlHash, task.getId());
                return task.getId();
            } catch (DuplicateKeyException e) {
                // 并发/窗口竞态：回退 DB 复核（复用或命中同 job 既有行）
                log.info("[createTaskIfAbsent][urlHash={}] 直插撞唯一键，回退 DB 复核复用", urlHash);
            }
        }
        // 同 URL 未终态任务直接复用（防并发重复抓同一 URL；设计 §二/§三：urlHash+fetchedAt 幂等）
        CrawlTaskDO existing = crawlTaskMapper.selectOne(new LambdaQueryWrapper<CrawlTaskDO>()
                .eq(CrawlTaskDO::getUrlHash, urlHash)
                .in(CrawlTaskDO::getStatus, CrawlTaskStatusEnum.PENDING.getStatus(),
                        CrawlTaskStatusEnum.RUNNING.getStatus())
                .last("LIMIT 1"));
        if (existing != null) {
            markBloom(urlHash);
            log.info("[createTaskIfAbsent][urlHash={}] 命中未终态任务 taskId={}，复用", urlHash, existing.getId());
            return existing.getId();
        }
        // 新任务：唯一键含 job 维度（uk_task_job_url）——同一 URL 跨批次（如二轮调度）落新任务行，不再与历史 SUCCESS/FAILED 行互斥
        try {
            CrawlTaskDO task = newTask(normalizedJob, sourceCode, url, urlHash, channelCode, parserCode, priority);
            crawlTaskMapper.insert(task);
            markBloom(urlHash);
            log.info("[createTaskIfAbsent][urlHash={}] 新任务 taskId={}（job={}）", urlHash, task.getId(), normalizedJob);
            return task.getId();
        } catch (DuplicateKeyException e) {
            // 同 job 同 URL 撞唯一键（同轮重复投递）：复用该 job 下既有任务行（DB 兜底，不抛裸异常）
            CrawlTaskDO sameJob = crawlTaskMapper.selectOne(new LambdaQueryWrapper<CrawlTaskDO>()
                    .eq(CrawlTaskDO::getJobId, normalizedJob)
                    .eq(CrawlTaskDO::getUrlHash, urlHash)
                    .last("LIMIT 1"));
            if (sameJob != null) {
                markBloom(urlHash);
                log.info("[createTaskIfAbsent][urlHash={}] 同 job 重复投递：复用 taskId={}", urlHash, sameJob.getId());
                return sameJob.getId();
            }
            throw new IllegalStateException("任务唯一键冲突后回查不到既有行 urlHash=" + urlHash, e);
        }
    }

    @Override
    public PageResult<CrawlTaskRespVO> getTaskPage(CrawlTaskPageReqVO reqVO) {
        LambdaQueryWrapper<CrawlTaskDO> wrapper = new LambdaQueryWrapper<>();
        if (reqVO.getJobId() != null) {
            wrapper.eq(CrawlTaskDO::getJobId, reqVO.getJobId());
        }
        if (StringUtils.hasText(reqVO.getSourceCode())) {
            wrapper.eq(CrawlTaskDO::getSourceCode, reqVO.getSourceCode());
        }
        if (StringUtils.hasText(reqVO.getStatus())) {
            wrapper.eq(CrawlTaskDO::getStatus, reqVO.getStatus());
        }
        if (StringUtils.hasText(reqVO.getChannelCode())) {
            wrapper.eq(CrawlTaskDO::getChannelCode, reqVO.getChannelCode());
        }
        if (StringUtils.hasText(reqVO.getUrl())) {
            wrapper.like(CrawlTaskDO::getUrl, reqVO.getUrl());
        }
        if (reqVO.getBeginTime() != null) {
            wrapper.ge(CrawlTaskDO::getCreateTime, reqVO.getBeginTime());
        }
        if (reqVO.getEndTime() != null) {
            wrapper.le(CrawlTaskDO::getCreateTime, reqVO.getEndTime());
        }
        wrapper.orderByDesc(CrawlTaskDO::getId);
        IPage<CrawlTaskDO> page = crawlTaskMapper.selectPage(Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toRespVO).toList(), page.getTotal());
    }

    @Override
    public CrawlTaskRespVO getTask(Long id) {
        CrawlTaskDO task = crawlTaskMapper.selectById(id);
        if (task == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_TASK_NOT_EXISTS);
        }
        return toRespVO(task);
    }

    @Override
    public void retryTask(Long id) {
        CrawlTaskDO task = crawlTaskMapper.selectById(id);
        if (task == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_TASK_NOT_EXISTS);
        }
        if (!CrawlTaskStatusEnum.FAILED.getStatus().equals(task.getStatus())) {
            // 仅重试耗尽/死信可人工重试；PENDING 本身在队列、SUCCESS/SKIPPED 终态无需重试（语义防呆）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.CRAWL_TASK_NOT_RETRYABLE);
        }
        String history = task.getMessage();
        // attempt 归零：人工放行“新一轮 ≤3 次尝试”（历史 message 保留便于追查原失败原因）
        task.setStatus(CrawlTaskStatusEnum.PENDING.getStatus());
        task.setAttempt(0);
        task.setNextRetryTime(null);
        task.setWorkerId(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setMessage("manual-retry（人工复核后放行新一轮）| 原:" + (StringUtils.hasText(history) ? history : "无"));
        crawlTaskMapper.updateById(task);
        log.info("[retryTask][taskId={} url={}] 死信任务人工重试 → PENDING（attempt=0）", task.getId(), task.getUrl());
    }

    private CrawlTaskRespVO toRespVO(CrawlTaskDO task) {
        CrawlTaskRespVO resp = new CrawlTaskRespVO();
        resp.setId(task.getId());
        resp.setJobId(task.getJobId());
        resp.setSourceCode(task.getSourceCode());
        resp.setUrl(task.getUrl());
        resp.setUrlHash(task.getUrlHash());
        resp.setChannelCode(task.getChannelCode());
        resp.setParserCode(task.getParserCode());
        resp.setPriority(task.getPriority());
        resp.setStatus(task.getStatus());
        resp.setAttempt(task.getAttempt());
        resp.setNextRetryTime(task.getNextRetryTime());
        resp.setWorkerId(task.getWorkerId());
        resp.setStartedAt(task.getStartedAt());
        resp.setFinishedAt(task.getFinishedAt());
        resp.setMessage(task.getMessage());
        resp.setPayloadJson(task.getPayloadJson());
        resp.setCreateTime(task.getCreateTime());
        resp.setUpdateTime(task.getUpdateTime());
        return resp;
    }

    private void markBloom(String urlHash) {
        if (urlBloomFilter != null) {
            urlBloomFilter.mark(urlHash);
        }
    }

    private CrawlTaskDO newTask(Long jobId, String sourceCode, String url, String urlHash,
                                String channelCode, String parserCode, Integer priority) {
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
        return task;
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
