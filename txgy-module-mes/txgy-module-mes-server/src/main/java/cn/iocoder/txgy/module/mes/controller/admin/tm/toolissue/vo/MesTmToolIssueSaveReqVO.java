package cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 工具领用单新增/修改 Request VO")
@Data
public class MesTmToolIssueSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "领用单编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "领用单编码不能为空")
    @Size(max = 64)
    private String code;

    @Schema(description = "领用单名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "领用单名称不能为空")
    @Size(max = 255)
    private String name;

    @Schema(description = "工位ID")
    private Long workstationId;

    @Schema(description = "领用日期")
    private LocalDateTime issueDate;

    @Schema(description = "备注")
    @Size(max = 500)
    private String remark;
}
