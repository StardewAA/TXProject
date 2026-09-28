package cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - MES 工具归还单行新增/修改 Request VO")
@Data
public class MesTmToolReturnLineSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "归还单ID")
    private Long returnId;

    @Schema(description = "工具ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "工具不能为空")
    private Long toolId;

    @Schema(description = "归还数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "归还数量不能为空")
    private Integer quantity;

    @Schema(description = "备注")
    private String remark;
}
