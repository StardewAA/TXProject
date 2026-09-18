package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Schema(description = "管理后台 - MES 项目生产统计详情 Response VO（工单进度 + 报工汇总）")
@Data
@EqualsAndHashCode(callSuper = true)
public class MesProProjectStatisticsDetailRespVO extends MesProProjectStatisticsRespVO {

    @Schema(description = "报工单总数", example = "10")
    private Integer feedbackTotal;

    @Schema(description = "草稿报工单数", example = "1")
    private Integer feedbackPrepareCount;

    @Schema(description = "审核中报工单数", example = "2")
    private Integer feedbackApprovingCount;

    @Schema(description = "已完成报工单数", example = "5")
    private Integer feedbackFinishedCount;

    @Schema(description = "报工数量合计", example = "500.00")
    private BigDecimal feedbackQuantityTotal;

    @Schema(description = "合格品数量合计", example = "480.00")
    private BigDecimal qualifiedQuantityTotal;

    @Schema(description = "不合格品数量合计", example = "20.00")
    private BigDecimal unqualifiedQuantityTotal;

    @Schema(description = "合格率百分比", example = "96.0")
    private BigDecimal qualifyRate;

}
