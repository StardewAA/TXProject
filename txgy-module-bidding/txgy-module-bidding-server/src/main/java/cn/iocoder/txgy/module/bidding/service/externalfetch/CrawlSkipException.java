package cn.iocoder.txgy.module.bidding.service.externalfetch;

/**
 * 规则性跳过（**不是失败**）。
 *
 * <p>规范依据：《外部信息获取 v1.0》§合规（按目标站点 robots.txt 约定抓取）+ §三 任务状态机
 * （PENDING→RUNNING→SUCCESS/FAILED/**SKIPPED**）。
 *
 * <p>业务目标：把"这条目标永远抓不到/不允许抓"与"这次抓取运气不好失败了"彻底分开。
 *
 * <p>原因（2026-09-11 缺陷修复）：原先 robots 禁止只抛 {@link IllegalStateException}，被监听器按普通异常处理，
 * 于是同一条被禁止的页面会：① 先占掉一个 2 分钟合规下载额度（闸位在更早处已占）；
 * ② 按失败重试 3 次（每次必然同样被禁止）；③ 最后进死信转人工审核——三重浪费，且台账上是"失败"而非"跳过"，
 * 运维无法一眼看出"这是合规跳过还是站点故障"。
 *
 * <p>处理约定：监听器捕获本异常 → 任务置 {@code SKIPPED}（终态），不计失败次数、不进死信、不写下载审计的失败行。
 */
public class CrawlSkipException extends RuntimeException {

    public CrawlSkipException(String message) {
        super(message);
    }

    public CrawlSkipException(String message, Throwable cause) {
        super(message, cause);
    }
}
