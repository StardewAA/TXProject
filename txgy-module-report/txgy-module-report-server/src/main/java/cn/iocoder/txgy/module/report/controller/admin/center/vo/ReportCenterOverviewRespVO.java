package cn.iocoder.txgy.module.report.controller.admin.center.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 报表中心 - 概览统计 Response VO
 *
 * 全部指标由服务端聚合得出，前端不做二次计算：
 *  - 报表总数 / 常用报表：来自报表清单
 *  - 今日查询 / 最近更新时间：来自报表执行日志已有的 create_time
 *    （日志表与字段均为既有，本次不新增任何数据库字段）
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表中心概览统计")
@Data
public class ReportCenterOverviewRespVO {

    @Schema(description = "可查看的报表总数", example = "18")
    private Integer reportTotal;

    @Schema(description = "常用报表数量（近 30 天被查询过）", example = "6")
    private Integer frequentCount;

    @Schema(description = "今日查询次数（当日零点起的成功查询）", example = "12")
    private Integer todayQueryCount;

    @Schema(description = "最近更新时间（yyyy-MM-dd HH:mm:ss；无查询记录时为空）",
            example = "2026-09-22 14:30:00")
    private String lastUpdateTime;

}
