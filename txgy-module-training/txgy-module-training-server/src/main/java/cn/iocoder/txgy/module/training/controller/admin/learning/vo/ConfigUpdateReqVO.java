package cn.iocoder.txgy.module.training.controller.admin.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - 学习全局配置更新 Request VO")
@Data
public class ConfigUpdateReqVO {

    @Schema(description = "配置键", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "danmaku_enabled", allowableValues = {"danmaku_enabled", "verify_interval_min",
            "heartbeat_interval_seconds", "max_delta_seconds", "session_timeout_seconds",
            "doc_min_stay_seconds", "video_complete_ratio", "doc_complete_ratio", "min_valid_seconds_checkin"})
    @NotBlank(message = "配置键不能为空")
    private String key;

    @Schema(description = "配置值", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotBlank(message = "配置值不能为空")
    private String value;

}
