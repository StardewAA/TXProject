package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "管理后台 - MES 首页点检保养执行 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeCheckMaintenRespVO {

    @Schema(description = "点检方案总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Long planTotalCount;

    @Schema(description = "已启用方案数", requiredMode = Schema.RequiredMode.REQUIRED, example = "9")
    private Long planEnabledCount;

    @Schema(description = "点检记录总数（近 N 天）", requiredMode = Schema.RequiredMode.REQUIRED, example = "46")
    private Long checkTotalCount;

    @Schema(description = "点检已完成数", requiredMode = Schema.RequiredMode.REQUIRED, example = "40")
    private Long checkFinishedCount;

    @Schema(description = "点检完成率（%）", requiredMode = Schema.RequiredMode.REQUIRED, example = "87.0")
    private BigDecimal checkFinishRate;

    @Schema(description = "保养记录总数（近 N 天）", requiredMode = Schema.RequiredMode.REQUIRED, example = "18")
    private Long maintenTotalCount;

    @Schema(description = "保养已提交数", requiredMode = Schema.RequiredMode.REQUIRED, example = "15")
    private Long maintenSubmittedCount;

    @Schema(description = "保养提交率（%）", requiredMode = Schema.RequiredMode.REQUIRED, example = "83.3")
    private BigDecimal maintenSubmitRate;

}
