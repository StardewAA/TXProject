package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 问题项复核进度统计（「问题复核」页顶部四个指标）。
 *
 * <p>方案依据：标书审查-管理端重新设计 §4.3.2（待复核/已确认/已忽略/已整改）。
 * <p>为什么单独出接口：若前端按 4 种 status 各打一次分页接口取 total，
 * 每次都要走一遍分页 COUNT + LIMIT 查询，属于把 1 次聚合拆成 4 次往返。
 */
@Schema(description = "管理后台 - 标书审查问题项复核统计 Response VO")
@Data
public class BidReviewIssueStatsRespVO {

    @Schema(description = "待复核数（OPEN）")
    private Long openCount;
    @Schema(description = "已确认数（CONFIRMED）")
    private Long confirmedCount;
    @Schema(description = "已忽略数（IGNORED，复核判定为误报）")
    private Long ignoredCount;
    @Schema(description = "已整改数（FIXED）")
    private Long fixedCount;
    @Schema(description = "问题项总数")
    private Long totalCount;
    @Schema(description = "证据不足数（AI 给不出标书原文证据，仍按原级别计入结论）")
    private Long evidenceLackCount;
}
