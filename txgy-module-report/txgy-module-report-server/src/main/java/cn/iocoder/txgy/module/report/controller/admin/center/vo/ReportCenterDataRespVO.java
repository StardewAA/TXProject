package cn.iocoder.txgy.module.report.controller.admin.center.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 报表中心 - 查询结果 Response VO
 *
 * 已按报表定义整理为「中文表头 + 有序行数据」，前端直接渲染表格或导出，无需理解字段名。
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表中心查询结果")
@Data
public class ReportCenterDataRespVO {

    @Schema(description = "中文表头（顺序与数据列一致）")
    private List<String> headers;

    @Schema(description = "数据行（每行按表头顺序排列）")
    private List<List<Object>> rows;

    @Schema(description = "总行数", example = "27")
    private Integer rowCount;

    @Schema(description = "查询耗时（毫秒）", example = "35")
    private Long durationMs;

}
