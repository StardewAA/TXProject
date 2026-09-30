package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 安许风险大盘统计出参（五页改造缺接口契约 Q19，2026-09-28）。
 *
 * <p>规范出处：Q19 契约——出参 { high, medium, low, handled, total }。
 * <p>口径：high/medium/low 按工单 risk_level（MAJOR 并入 high；LOW 独立）；
 * handled=已处理（HANDLED）工单数；total=全部工单数（未删除）。
 */
@Schema(description = "管理后台 - 安许风险大盘统计出参")
@Data
public class SafetyRiskItemStatsRespVO {

    @Schema(description = "高危（HIGH+MAJOR）", example = "3")
    private Long high;

    @Schema(description = "中危（MEDIUM）", example = "2")
    private Long medium;

    @Schema(description = "低危（LOW）", example = "5")
    private Long low;

    @Schema(description = "已处理（HANDLED）", example = "4")
    private Long handled;

    @Schema(description = "工单总数", example = "12")
    private Long total;
}
