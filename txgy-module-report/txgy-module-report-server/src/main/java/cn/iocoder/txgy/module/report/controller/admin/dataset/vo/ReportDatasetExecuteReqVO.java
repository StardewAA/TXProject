package cn.iocoder.txgy.module.report.controller.admin.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

/**
 * 报表数据集 - 执行查询 Request VO
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表数据集执行查询 Request VO")
@Data
public class ReportDatasetExecuteReqVO {

    @Schema(description = "数据集编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "mes_feedback_detail")
    @NotEmpty(message = "数据集编码不能为空")
    private String code;

    @Schema(description = "查询参数（键为数据集声明的参数名）")
    private Map<String, Object> params;

}
