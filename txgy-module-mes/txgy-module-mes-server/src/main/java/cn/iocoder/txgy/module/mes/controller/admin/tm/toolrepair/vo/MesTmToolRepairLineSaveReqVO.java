package cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - MES 工具维修单行新增/修改 Request VO")
@Data
public class MesTmToolRepairLineSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "维修单ID")
    private Long repairId;

    @Schema(description = "工具ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "工具不能为空")
    private Long toolId;

    @Schema(description = "维修数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "维修数量不能为空")
    private Integer quantity;

    @Schema(description = "维修结果")
    private String repairResult;

    @Schema(description = "备注")
    private String remark;
}
