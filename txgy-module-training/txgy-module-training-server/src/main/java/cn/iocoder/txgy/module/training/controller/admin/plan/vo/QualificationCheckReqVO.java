package cn.iocoder.txgy.module.training.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 上岗资格核查 Request VO（人员 ID + 项目 ID）")
@Data
public class QualificationCheckReqVO {

    @Schema(description = "人员（学员用户）ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "3001")
    @NotNull(message = "人员 ID 不能为空")
    private Long userId;

    @Schema(description = "工程项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROJ-2026-001")
    @NotEmpty(message = "项目 ID 不能为空")
    private String projectId;

}
