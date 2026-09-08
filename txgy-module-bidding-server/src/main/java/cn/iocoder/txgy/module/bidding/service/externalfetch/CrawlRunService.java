package cn.iocoder.txgy.module.bidding.service.externalfetch;
// [依据] 下载器触发与调度：人工触发端点 + 定时轮询（源级间隔 ≥120s，见频控约束）；调度开关 bidding.crawl.scheduler.enabled（默认关）。

import cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo.CrawlRunRespVO;

public interface CrawlRunService {

    /** 立即执行一个采集源的一轮采集（人工触发；同步返回汇总 VO，落 MANUAL 批次） */
    CrawlRunRespVO runNow(String sourceCode);

    /** 执行一轮并落批次（调度/人工共用：MANUAL/SCHEDULE；每次执行生成 bidding_fetch_job 记录） */
    CrawlRunRespVO runNow(String sourceCode, String triggerType);

    /**
     * 消费驱动批次收尾：该 job 下无 PENDING/RUNNING task 时汇总收尾（SUCCESS/PARTIAL/FAILED）。
     * 异步投递模式下由消费者每完成一条 task 调用；同步模式不使用（runNow 直接收尾）。
     */
    void tryFinalizeJob(Long jobId);

}
