package cn.iocoder.txgy.module.bidding.controller.admin.processtrack.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 投标报名资质审核 Request（平台方执行）")
@Data
public class ProcessTrackReviewReqVO {

    @Schema(description = "投标流程跟踪ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "流程跟踪ID不能为空")
    private Long id;

    @Schema(description = "审核结果：APPROVED/REJECTED", requiredMode = Schema.RequiredMode.REQUIRED, example = "APPROVED")
    @NotBlank(message = "审核结果不能为空")
    private String reviewResult;

    @Schema(description = "审核说明/不通过原因（REJECTED 时必填）", example = "资质材料不完整")
    private String reviewReason;

    @Schema(description = "操作人ID（平台方审核员）", example = "1")
    private Long operatorId;
}
