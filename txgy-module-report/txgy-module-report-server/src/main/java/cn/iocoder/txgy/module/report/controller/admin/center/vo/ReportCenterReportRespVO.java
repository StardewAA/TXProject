package cn.iocoder.txgy.module.report.controller.admin.center.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 报表中心 - 报表清单 Response VO
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表中心报表清单")
@Data
public class ReportCenterReportRespVO {

    @Schema(description = "数据集编码（前端调用用）", example = "mes_daily_production")
    private String code;

    @Schema(description = "报表名称", example = "生产日报")
    private String name;

    @Schema(description = "报表用途说明", example = "按日期、工单、工序汇总的报工数量与合格情况")
    private String description;

    @Schema(description = "可筛选参数")
    private List<Param> params;

    @Schema(description = "业务分组（支持多归属，如 [\"生产\",\"库存\"]）", example = "[\"生产\"]")
    private List<String> groups;

    @Schema(description = "列中文标签（供前端搜索索引）", example = "[\"工单号\",\"计划数量\"]")
    private List<String> columnLabels;

    @Schema(description = "近 30 天查询次数（供「常用」排序）", example = "12")
    private Integer queryCount;

    /**
     * 报表筛选参数
     */
    @Schema(description = "报表筛选参数")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Param {

        @Schema(description = "参数名", example = "startDate")
        private String name;

        @Schema(description = "显示名", example = "开始日期")
        private String label;

    }

}
