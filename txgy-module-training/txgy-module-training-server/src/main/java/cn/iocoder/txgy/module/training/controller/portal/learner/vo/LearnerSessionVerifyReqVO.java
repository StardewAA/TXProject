package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "学员端 - 学习防挂机验证 Request VO")
@Data
public class LearnerSessionVerifyReqVO {

    @Schema(description = "学习会话 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "学习会话 ID 不能为空")
    private Long sessionId;

    @Schema(description = "答案（数学算式结果整数）", requiredMode = Schema.RequiredMode.REQUIRED, example = "56")
    @NotEmpty(message = "答案不能为空")
    private String answer;

}
