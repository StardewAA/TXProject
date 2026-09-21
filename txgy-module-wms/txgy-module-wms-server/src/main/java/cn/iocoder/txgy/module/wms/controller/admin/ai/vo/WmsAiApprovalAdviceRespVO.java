package cn.iocoder.txgy.module.wms.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 辅助审批 —— 审批建议响应
 *
 * <p>供审批人参考，不自动流转。{@link #suggestion} 取值：
 * <ul>
 *   <li>{@code APPROVE}  建议通过</li>
 *   <li>{@code REJECT}   建议驳回</li>
 *   <li>{@code MANUAL}   需要人工判断（信息不足或存在需人工核实的风险）</li>
 * </ul>
 */
@Schema(description = "管理后台 - WMS AI 辅助审批建议 Response VO")
@Data
public class WmsAiApprovalAdviceRespVO {

    @Schema(description = "建议结论：APPROVE / REJECT / MANUAL", example = "MANUAL")
    private String suggestion;

    @Schema(description = "结论中文描述", example = "需要人工判断")
    private String suggestionLabel;

    @Schema(description = "单据摘要（AI 生成的自然语言概述）")
    private String summary;

    @Schema(description = "风险与关注点列表")
    private List<Risk> risks = new ArrayList<>();

    @Schema(description = "给出该建议的理由")
    private String reason;

    @Schema(description = "是否成功获得 AI 建议；false 时 errorMessage 为原因", example = "true")
    private Boolean ok = Boolean.TRUE;

    @Schema(description = "失败原因（ok=false 时有值）")
    private String errorMessage;

    @Schema(description = "参与分析的单据明细行数", example = "1")
    private Integer detailCount;

    @Schema(description = "风险项")
    @Data
    public static class Risk {

        @Schema(description = "风险级别：HIGH 高 / MEDIUM 中 / LOW 低", example = "MEDIUM")
        private String level;

        @Schema(description = "风险标题", example = "库存不足")
        private String title;

        @Schema(description = "风险说明", example = "岩棉板 1200×600×50 当前库存 12，本单需出库 20，缺口 8")
        private String detail;

    }

}
