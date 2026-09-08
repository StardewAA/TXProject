package cn.iocoder.txgy.module.bidding.service.crawlexternal;
// [依据] 下载器触发与调度：人工触发端点 + 定时轮询（源级间隔 ≥120s，见频控约束）；调度开关 bidding.crawl.scheduler.enabled（默认关）。

import cn.iocoder.txgy.module.bidding.controller.admin.crawlexternal.vo.CrawlRunRespVO;

public interface CrawlRunService {

    /** 立即执行一个采集源的一轮采集（人工触发；同步返回汇总 VO，落 MANUAL 批次） */
    CrawlRunRespVO runNow(String sourceCode);

    /** 执行一轮并落批次（调度/人工共用：MANUAL/SCHEDULE；每次执行生成 bidding_crawl_job 记录） */
    CrawlRunRespVO runNow(String sourceCode, String triggerType);

}
