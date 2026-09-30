package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import lombok.Data;

import java.util.List;

/** 审查统计出参（管理端看板）。 */
@Data
public class BidReviewOverviewRespVO {

    /** 总任务数 */
    private Long totalCount;
    /** 执行中 */
    private Long runningCount;
    /** 已完成 */
    private Long successCount;
    /** 失败 */
    private Long failedCount;
    /** 结论分布 */
    private List<NameCount> verdictStats;
    /** 问题级别分布 */
    private List<NameCount> severityStats;
    /** 高频问题 Top N（按规则编码聚合） */
    private List<NameCount> topRules;
    /** AI 调用总次数（近 24h） */
    private Long aiCallsLast24h;
    /** AI 失败率（近 24h，0~1） */
    private Double aiFailureRate;

    @Data
    public static class NameCount {
        private String name;
        private Long count;
    }
}
