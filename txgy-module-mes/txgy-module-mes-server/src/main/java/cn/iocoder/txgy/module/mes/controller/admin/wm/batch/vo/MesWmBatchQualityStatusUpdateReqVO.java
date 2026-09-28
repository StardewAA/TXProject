package cn.iocoder.txgy.module.mes.controller.admin.wm.batch.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - MES 批次质量状态调整 Request VO")
@Data
public class MesWmBatchQualityStatusUpdateReqVO {

    @Schema(description = "批次编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "批次编号不能为空")
    private Long id;

    @Schema(description = "质量状态：0=待检 1=合格 2=不合格 3=冻结 4=隔离 5=让步",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "质量状态不能为空")
    private Integer qualityStatus;

}
