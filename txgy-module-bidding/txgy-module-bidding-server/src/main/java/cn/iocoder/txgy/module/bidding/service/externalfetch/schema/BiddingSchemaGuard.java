package cn.iocoder.txgy.module.bidding.service.externalfetch.schema;

// 规范依据：《招投标模块开发规范》§4.4（故障要让调用方看得见，不许静默降级）；用户反馈 2026-09-11：
//   「表 bidding_crawl_download_gate 不存在 → 任务被判定失败 → 120/240 秒后重试 → 重试又卡在同一步 → 无限循环」。
// 业务目标：把"缺表"这类部署期问题从**运行期反复失败**前移到**启动期一次性说清 + 停掉会空转的调度**。

/**
 * 库表自检结果持有者（进程内静态标志，由启动自检写入、调度与消费端读取）。
 *
 * <p>为什么用静态标志而不是 Spring 事件/开关 Bean：调度入口（{@code CrawlRunServiceImpl#scheduledScan}）
 * 在极早期就可能被触发，静态标志没有装配顺序问题；同时它只承载"能否跑爬虫"这一个布尔语义，
 * 不引入新的配置项（配置改不了库表是否存在）。
 */
public final class BiddingSchemaGuard {

    /** null=爬虫可用；非 null=不可用及原因 */
    private static volatile String crawlUnavailableReason = null;

    private BiddingSchemaGuard() {
    }

    /** 爬虫关键表是否齐备（调度与人工触发的前置判断） */
    public static boolean isCrawlSchemaReady() {
        return crawlUnavailableReason == null;
    }

    /** 标记爬虫不可用（缺关键表）；由启动自检调用 */
    public static void markCrawlSchemaUnavailable(String reason) {
        crawlUnavailableReason = reason;
    }

    /** 不可用原因（用于日志与接口报错文案） */
    public static String crawlUnavailableReason() {
        return crawlUnavailableReason;
    }

    /**
     * 复位（仅供单测使用）。
     *
     * <p>说明：本类是进程内静态状态，单测之间会互相污染（前一个用例标了不可用，后一个用例读到的就是脏值），
     * 因此显式提供复位入口；生产代码不应调用它——库表是否存在在进程生命周期内不会变化。
     */
    static void resetForTest() {
        crawlUnavailableReason = null;
    }

}
