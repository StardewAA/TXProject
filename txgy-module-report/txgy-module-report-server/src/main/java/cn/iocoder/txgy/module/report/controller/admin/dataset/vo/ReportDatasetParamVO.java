package cn.iocoder.txgy.module.report.controller.admin.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 报表数据集 - 参数定义 VO
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表数据集参数定义")
@Data
public class ReportDatasetParamVO {

    @Schema(description = "参数名（对应 SQL 中的 ${name} 占位）", requiredMode = Schema.RequiredMode.REQUIRED, example = "startTime")
    @NotEmpty(message = "参数名不能为空")
    private String name;

    @Schema(description = "参数显示名", example = "开始时间")
    private String label;

    @Schema(description = "参数类型：string/number/datetime/date", example = "datetime")
    private String type;

    @Schema(description = "是否必填", example = "false")
    private Boolean required;

    @Schema(description = "默认值", example = "2026-09-01")
    private String defaultValue;

}
