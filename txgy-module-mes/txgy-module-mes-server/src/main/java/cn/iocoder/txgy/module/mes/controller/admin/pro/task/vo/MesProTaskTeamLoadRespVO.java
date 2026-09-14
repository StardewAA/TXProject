package cn.iocoder.txgy.module.mes.controller.admin.pro.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - MES 班组任务负载 Response VO")
@Data
public class MesProTaskTeamLoadRespVO {

    @Schema(description = "班组编号", example = "1")
    private Long teamId;

    @Schema(description = "任务数量", example = "5")
    private Long taskCount;

    @Schema(description = "排产数量合计", example = "1000.00")
    private BigDecimal quantityTotal;

    @Schema(description = "已生产数量合计", example = "600.00")
    private BigDecimal producedTotal;

}
