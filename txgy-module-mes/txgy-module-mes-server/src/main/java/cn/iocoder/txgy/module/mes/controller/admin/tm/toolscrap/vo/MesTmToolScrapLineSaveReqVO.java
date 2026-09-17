package cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - MES 工具报废单行新增/修改 Request VO")
@Data
public class MesTmToolScrapLineSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "报废单ID")
    private Long scrapId;

    @Schema(description = "工具ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "工具不能为空")
    private Long toolId;

    @Schema(description = "报废数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "报废数量不能为空")
    private Integer quantity;

    @Schema(description = "报废原因")
    private String scrapReason;

    @Schema(description = "备注")
    private String remark;
}
