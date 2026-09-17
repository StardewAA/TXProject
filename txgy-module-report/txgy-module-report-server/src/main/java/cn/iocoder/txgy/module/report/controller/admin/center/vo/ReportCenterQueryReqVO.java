package cn.iocoder.txgy.module.report.controller.admin.center.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

/**
 * 报表中心 - 查询/导出 Request VO
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - 报表中心查询/导出 Request VO")
@Data
public class ReportCenterQueryReqVO {

    @Schema(description = "数据集编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "mes_daily_production")
    @NotEmpty(message = "报表编码不能为空")
    private String code;

    @Schema(description = "筛选参数（键为报表声明的参数名）")
    private Map<String, Object> params;

}
