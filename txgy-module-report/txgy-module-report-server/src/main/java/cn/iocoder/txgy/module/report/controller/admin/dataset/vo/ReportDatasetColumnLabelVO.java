package cn.iocoder.txgy.module.report.controller.admin.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报表数据集 - 列标签 VO
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表数据集列标签")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportDatasetColumnLabelVO {

    @Schema(description = "字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "feedback_code")
    @NotEmpty(message = "字段名不能为空")
    private String field;

    @Schema(description = "中文表头", requiredMode = Schema.RequiredMode.REQUIRED, example = "报工单号")
    @NotEmpty(message = "中文表头不能为空")
    private String label;

}
