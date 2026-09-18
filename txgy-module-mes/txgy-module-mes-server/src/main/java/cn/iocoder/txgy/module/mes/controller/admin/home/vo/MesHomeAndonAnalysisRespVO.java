package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - MES 首页安灯呼叫分析 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeAndonAnalysisRespVO {

    @Schema(description = "呼叫总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "36")
    private Long totalCount;

    @Schema(description = "未处置数", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    private Long activeCount;

    @Schema(description = "已处置数", requiredMode = Schema.RequiredMode.REQUIRED, example = "31")
    private Long handledCount;

    @Schema(description = "处置率（%）", requiredMode = Schema.RequiredMode.REQUIRED, example = "86.1")
    private BigDecimal handleRate;

    @Schema(description = "平均处置时长（分钟）", requiredMode = Schema.RequiredMode.REQUIRED, example = "42.5")
    private BigDecimal avgHandleMinutes;

    @Schema(description = "按级别分布", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<MesHomeAndonLevelStatRespVO> levelStats;

    @Schema(description = "呼叫原因 TOP N", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<MesHomeAndonReasonStatRespVO> topReasons;

}
