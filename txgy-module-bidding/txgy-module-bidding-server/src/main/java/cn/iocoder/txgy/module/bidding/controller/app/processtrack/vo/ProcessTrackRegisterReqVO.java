package cn.iocoder.txgy.module.bidding.controller.app.processtrack.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "用户 App - 投标报名 Request（意向投标→资质审核）")
@Data
public class ProcessTrackRegisterReqVO {

    @Schema(description = "投标流程跟踪ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "流程跟踪ID不能为空")
    private Long id;

    @Schema(description = "投标员工用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "用户ID不能为空")
    private Long userId;
}
