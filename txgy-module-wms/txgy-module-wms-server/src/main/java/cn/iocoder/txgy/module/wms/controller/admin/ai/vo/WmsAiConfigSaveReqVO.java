package cn.iocoder.txgy.module.wms.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * WMS AI 客服配置 —— 保存请求
 */
@Schema(description = "管理后台 - WMS AI 客服配置 Save Request VO")
@Data
public class WmsAiConfigSaveReqVO {

    @Schema(description = "启用的模型编号（来自 AI 模块的模型配置）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "22")
    @NotNull(message = "请选择要使用的模型")
    private Long modelId;

    @Schema(description = "是否启用界面配置：启用后忽略容器里的环境变量", example = "true")
    private Boolean enabled;

    @Schema(description = "调用超时（秒），留空用默认值", example = "240")
    private Integer timeoutSeconds;

    @Schema(description = "备注", example = "WMS 客服默认用豆包 lite")
    private String remark;

}
