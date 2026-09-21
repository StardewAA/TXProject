package cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - MES 工具归还单新增/修改 Request VO")
@Data
public class MesTmToolReturnSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "归还单编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "归还单编码不能为空")
    @Size(max = 64)
    private String code;

    @Schema(description = "归还单名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "归还单名称不能为空")
    @Size(max = 255)
    private String name;

    @Schema(description = "工位ID")
    private Long workstationId;

    @Schema(description = "归还日期")
    private LocalDateTime returnDate;

    @Schema(description = "备注")
    @Size(max = 500)
    private String remark;
}
