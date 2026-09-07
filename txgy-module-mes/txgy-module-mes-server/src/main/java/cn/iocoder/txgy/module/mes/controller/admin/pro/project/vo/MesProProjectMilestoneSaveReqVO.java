package cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Schema(description = "管理后台 - MES 项目里程碑新增/修改 Request VO")
@Data
public class MesProProjectMilestoneSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "里程碑名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "完成钢管保温加工")
    @NotEmpty(message = "里程碑名称不能为空")
    private String name;

    @Schema(description = "计划完成日期", example = "2026-10-01 00:00:00")
    private LocalDateTime planDate;

    @Schema(description = "实际完成日期", example = "2026-10-03 00:00:00")
    private LocalDateTime actualDate;

    @Schema(description = "里程碑状态：0未开始 1进行中 2已完成", example = "0")
    private Integer status;

    @Schema(description = "排序（升序）", example = "1")
    private Integer sort;

    @Schema(description = "备注", example = "备注")
    private String remark;

}
