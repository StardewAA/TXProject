package cn.iocoder.txgy.module.report.controller.admin.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 报表数据集 - 执行查询 Response VO
 *
 * 字段与 GoView 大屏数据格式保持一致，便于大屏直接引用。
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表数据集执行查询 Response VO")
@Data
public class ReportDatasetExecuteRespVO {

    @Schema(description = "列名列表")
    private List<String> dimensions;

    @Schema(description = "数据明细")
    private List<Map<String, Object>> source;

    @Schema(description = "返回行数", example = "128")
    private Integer rowCount;

    @Schema(description = "查询耗时（毫秒）", example = "35")
    private Long durationMs;

}
