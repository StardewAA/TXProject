package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - MES 首页维修工单分析 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeRepairAnalysisRespVO {

    @Schema(description = "维修工单总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    private Long totalCount;

    @Schema(description = "待处理数（草稿 + 维修中 + 待验收）", requiredMode = Schema.RequiredMode.REQUIRED, example = "7")
    private Long pendingCount;

    @Schema(description = "已完成数（已确认）", requiredMode = Schema.RequiredMode.REQUIRED, example = "13")
    private Long finishedCount;

    @Schema(description = "平均维修时长（小时，报修 → 完成）", requiredMode = Schema.RequiredMode.REQUIRED, example = "6.5")
    private BigDecimal avgRepairHours;

    @Schema(description = "维修通过数", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Long passCount;

    @Schema(description = "维修不通过数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long failCount;

    @Schema(description = "按状态分布", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<MesHomeRepairStatusStatRespVO> statusStats;

}
