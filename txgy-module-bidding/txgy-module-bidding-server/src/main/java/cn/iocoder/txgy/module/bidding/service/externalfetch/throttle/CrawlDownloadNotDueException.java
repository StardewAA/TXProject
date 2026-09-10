package cn.iocoder.txgy.module.bidding.service.externalfetch.throttle;

// 规范依据：用户口径 2026-09-10「排队中的任务绝对不允许直接触发下载」「每次下载原文文件前必须强制休眠等待」。
// 业务目标：把"未到合规下载间隔"表达为**可恢复的排队信号**，而不是失败——
//   任务回到 PENDING（不计尝试次数、不进退避、不进死信），到点由扫描按 id 升序继续。

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 未到原文下载间隔（合规限速闸未放行）。
 *
 * <p>语义区分：这不是"失败"，而是"尚未轮到你下载"。消费者捕获后应把任务放回 PENDING，
 * 并把 next_retry_time 置为 {@link #dueAt}（到点自动续跑），<b>不得</b>计入失败重试次数。
 */
@Getter
public class CrawlDownloadNotDueException extends RuntimeException {

    /** 下次可下载时刻（null=仅闸门不可用等异常情形，调用方按短暂退避处理） */
    private final LocalDateTime dueAt;
    /** 涉及的下载地址（日志/审计） */
    private final String url;

    public CrawlDownloadNotDueException(LocalDateTime dueAt, String url) {
        super("未到合规下载间隔（下载原文文件限速）：可下载时间=" + dueAt + " url=" + url);
        this.dueAt = dueAt;
        this.url = url;
    }

}
