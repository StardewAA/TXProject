package cn.iocoder.txgy.module.report.controller.admin.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Report dataset simple-mode create request.
 *
 * Staff pick a whitelisted table and fields; SQL is generated server-side.
 */
@Schema(description = "report center simple-mode dataset create")
@Data
public class SimpleDatasetReqVO {

    @Schema(description = "dataset code (auto if blank)", example = "mes_feedback_daily")
    private String code;

    @Schema(description = "dataset name", requiredMode = Schema.RequiredMode.REQUIRED, example = "baoGongRiBao")
    private String name;

    @Schema(description = "category", example = "MES")
    private String category;

    @Schema(description = "whitelisted table", requiredMode = Schema.RequiredMode.REQUIRED, example = "mes_pro_feedback")
    private String tableName;

    @Schema(description = "fields to show (must be in table catalog)", requiredMode = Schema.RequiredMode.REQUIRED)
    private java.util.List<String> fields;

    @Schema(description = "optional date column for date filter", example = "feedback_time")
    private String dateField;

    @Schema(description = "owner", example = "zhangsan")
    private String owner;

    @Schema(description = "remark")
    private String remark;

}
