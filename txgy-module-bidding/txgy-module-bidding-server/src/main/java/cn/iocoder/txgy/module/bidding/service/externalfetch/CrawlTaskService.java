package cn.iocoder.txgy.module.bidding.service.externalfetch;
// 规范依据：设计文档 v0.6 §二/§三/§四/§15.4（采集任务状态机、urlHash 幂等去重、MQ 3 次重试→死信转审核）

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlTaskRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlTaskDO;

import java.time.LocalDateTime;

/**
 * 采集任务编排服务。
 *
 * <p>核心语义（与消息/重试对齐，设计 §三）：
 * <ul>
 *   <li>urlHash 幂等去重：同 URL 存在未终态（PENDING/RUNNING）任务直接复用，不重复抓；</li>
 *   <li>失败重试：attempt 指数退避，attempt &lt; 3 → 回 PENDING+nextRetryTime（等价 crawl.retry 重投）；</li>
 *   <li>attempt ≥ 3（耗尽）→ FAILED 死信，经 {@code CrawlDeadLetterSink} 转人工审核（crawl.audit）；</li>
 *   <li>管理面：FAILED（死信/审核队列）可人工重试 → PENDING 新轮次（attempt 归零，视为人工放行一轮新的 ≤3 次尝试）。</li>
 * </ul>
 */
public interface CrawlTaskService {

    /**
     * 建/取任务：同 urlHash 存在 PENDING/RUNNING 时复用（幂等去重）。
     *
     * @return 任务 ID（新建或复用）
     */
    Long createTaskIfAbsent(Long jobId, String sourceCode, String url, String urlHash,
                            String channelCode, String parserCode, Integer priority);

    /**
     * 标记开始执行（RUNNING + worker 归属）。
     */
    void markRunning(Long taskId, String workerId);

    /**
     * 原子抢占执行权（仅 PENDING → RUNNING）。
     *
     * <p>为什么需要它：原 {@link #markRunning} 是"先读后写"的无条件更新，同一任务被并发捞出时
     * 两个执行者都能置 RUNNING 并各自发起一次原文下载——既是数据重复，也对目标站点构成无谓压力（合规风险）。
     * 条件更新（WHERE status='PENDING'）由 InnoDB 行锁保证只有一个执行者成功。
     *
     * @return true=抢占成功（本次由我执行）；false=已被他人抢占或已终态，调用方必须放弃本次执行
     */
    boolean tryMarkRunning(Long taskId, String workerId);

    /**
     * 未到合规下载间隔：把任务放回 PENDING 排队（**不计**尝试次数、不进失败退避、不进死信）。
     *
     * <p>规范依据：用户口径 2026-09-10「排队中的任务绝对不允许直接触发下载」「必须确保任务按顺序流转」。
     * 语义：这不是失败，而是"尚未轮到下载"；next_retry_time 置为下次可下载时刻，到点由扫描按 id 升序续跑。
     */
    void releaseToPending(Long taskId, LocalDateTime nextRetryTime, String reason);

    /**
     * 下载成功：置 SUCCESS 并记录原文引用/内容形态（原文落库/抽取由下游消费驱动，见设计 §四）。
     */
    void markDownloadSuccess(Long taskId, String contentRef, String contentType);

    /**
     * 规则性跳过（**不是失败**）：置 SKIPPED 终态，不计尝试次数、不进失败退避、不进死信。
     *
     * <p>规范依据：《外部信息获取 v1.0》§合规（robots 约定）+ §三 状态机（SUCCESS/FAILED/SKIPPED）。
     * 业务目标：把"永远不该抓/不允许抓"（如 robots.txt Disallow、增量未变）与"这次抓取失败了"分开，
     * 否则被禁止的页面会被反复重试并最终堆进人工审核队列，掩盖真正的站点故障。
     */
    void markSkipped(Long taskId, String reason);

    /**
     * 处理下载/处理失败：3 次内退避重试；耗尽置 FAILED 并转人工审核。
     *
     * @return 重试中 或 死信转审核
     */
    CrawlAttemptOutcome handleFailure(Long taskId, String cause);

    /**
     * 处理失败（尊重站点声明的冷却时间）：实际等待 = max(指数退避, 站点 Retry-After)。
     *
     * <p>规范依据：《外部信息获取 v1.0》§合规（频控）；HTTP 429/503 的 Retry-After 是站点的明确要求。
     * 业务目标：被反爬警告后不得按"固定 2 分钟"继续敲同一站点——反爬拦截的冷却下限为 10 分钟（监听器传入）。
     *
     * @param retryAfterSeconds 站点/异常声明的最小冷却秒数；null=无特殊要求，按指数退避
     */
    CrawlAttemptOutcome handleFailure(Long taskId, String cause, Integer retryAfterSeconds);

    /** 处理结果分类（对外断言用） */
    enum CrawlAttemptOutcome {
        /** 已安排第 N 次重试（PENDING + nextRetryTime） */
        RETRY_SCHEDULED,
        /** 重试耗尽（≥3 次）→ FAILED + 转人工审核 */
        DEAD_LETTER_AUDITED
    }

    /** 采集任务管理分页（管理面：按批次/源/状态/URL 检索，定位死信与重试对象） */
    PageResult<CrawlTaskRespVO> getTaskPage(CrawlTaskPageReqVO reqVO);

    /** 采集任务详情 */
    CrawlTaskRespVO getTask(Long id);

    /** 人工重试：仅 FAILED（死信）→ PENDING，attempt 归零开启新一轮 ≤3 次尝试（审核/人工复核后放行） */
    void retryTask(Long id);

}
