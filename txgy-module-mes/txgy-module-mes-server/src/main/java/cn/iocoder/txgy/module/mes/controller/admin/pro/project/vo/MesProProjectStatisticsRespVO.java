package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - MES 项目生产进度统计 Response VO")
@Data
public class MesProProjectStatisticsRespVO {

    @Schema(description = "项目编号", example = "1024")
    private Long projectId;

    @Schema(description = "生产工单总数（含已取消）", example = "5")
    private Integer workOrderTotal;

    @Schema(description = "草稿工单数", example = "1")
    private Integer prepareCount;

    @Schema(description = "已确认(进行中)工单数", example = "2")
    private Integer confirmedCount;

    @Schema(description = "已完成工单数", example = "1")
    private Integer finishedCount;

    @Schema(description = "已取消工单数", example = "1")
    private Integer canceledCount;

    @Schema(description = "生产数量合计", example = "1000.00")
    private BigDecimal quantityTotal;

    @Schema(description = "已完成生产数量合计", example = "600.00")
    private BigDecimal quantityProducedTotal;

}
