package cn.iocoder.txgy.module.trade.controller.app.checkout.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "用户 App - 平台结算正式化准备 Request VO")
@Data
public class AppPlatformCheckoutFormalizationPrepareReqVO {

    @Schema(description = "本次正式化准备的客户端幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "幂等键不能为空")
    @Size(min = 8, max = 64, message = "幂等键长度必须为 8 到 64 个字符")
    @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "幂等键格式不正确")
    private String idempotencyKey;
}
