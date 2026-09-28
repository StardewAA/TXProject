package cn.iocoder.txgy.module.mes.controller.admin.pro.andon.vo.record;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 现场异常总览 Response VO（现场过程状态统一看板）")
@Data
public class MesProAndonRecordOverviewRespVO {

    @Schema(description = "未处置安灯总数", example = "5")
    private Long pendingCount;

    @Schema(description = "超时未处置数（已达 SLA 仍未处理）", example = "2")
    private Long overdueCount;

    @Schema(description = "已超时升级未处置数", example = "1")
    private Long escalatedCount;

    @Schema(description = "今日已处置数", example = "8")
    private Long handledTodayCount;

    @Schema(description = "统计时间")
    private LocalDateTime statisticsTime;

}
