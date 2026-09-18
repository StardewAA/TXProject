package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "管理后台 - MES 首页生产交付达成分析 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeDeliveryAnalysisRespVO {

    @Schema(description = "近 N 天完成工单数", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Long totalCount;

    @Schema(description = "按期完成数（完成时间不晚于需求日期）", requiredMode = Schema.RequiredMode.REQUIRED, example = "8")
    private Long onTimeCount;

    @Schema(description = "超期完成数", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    private Long overdueCount;

    @Schema(description = "按期完成率（%）", requiredMode = Schema.RequiredMode.REQUIRED, example = "66.7")
    private BigDecimal onTimeRate;

    @Schema(description = "在制超期数（已过需求日期但尚未完成）", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Long overdueOpenCount;

    @Schema(description = "超期工单平均延期天数", requiredMode = Schema.RequiredMode.REQUIRED, example = "4.5")
    private BigDecimal avgOverdueDays;

}
