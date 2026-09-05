package cn.iocoder.txgy.module.bidding.enums;
// 规范依据：设计文档 v0.6 §三/§四（task 状态机 PENDING→RUNNING→SUCCESS/FAILED/SKIPPED）；DDL v0.6

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 采集任务状态（表 bidding_crawl_task.status）。
 */
@Getter
@AllArgsConstructor
public enum CrawlTaskStatusEnum {

    PENDING("PENDING", "待处理（含等待重试）"),
    RUNNING("RUNNING", "执行中（worker 归属）"),
    SUCCESS("SUCCESS", "成功（原文已落库/转审核入口）"),
    FAILED("FAILED", "失败（重试耗尽=死信，转人工审核）"),
    SKIPPED("SKIPPED", "跳过（增量未变/不符合源规则）");

    private final String status;
    private final String name;

    public static CrawlTaskStatusEnum of(String status) {
        return Arrays.stream(values())
                .filter(item -> item.getStatus().equals(status))
                .findFirst()
                .orElse(null);
    }

}
