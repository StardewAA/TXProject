package cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 资格预审申请审查 Request（资格审查委员会）")
@Data
public class PrequalReviewReqVO {

    @Schema(description = "申请ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "申请ID不能为空")
    private Long applyId;

    @Schema(description = "是否通过", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审查结论不能为空")
    private Boolean passed;

    @Schema(description = "审查说明/不通过原因")
    private String reviewReason;
}
