package cn.iocoder.txgy.module.wms.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * WMS AI 客服配置 —— 查询响应
 *
 * <p>把「配了什么模型、来源是界面还是环境变量」一次讲清楚，便于排查
 * 「改了配置为什么不生效」这类问题。
 */
@Schema(description = "管理后台 - WMS AI 客服配置 Response VO")
@Data
public class WmsAiConfigRespVO {

    @Schema(description = "配置主键（无配置时为 null）", example = "1")
    private Long id;

    @Schema(description = "是否启用界面配置", example = "true")
    private Boolean enabled;

    @Schema(description = "调用超时（秒）", example = "240")
    private Integer timeoutSeconds;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "配置的模型编号", example = "22")
    private Long modelId;

    @Schema(description = "模型名称（来自 AI 模块）", example = "doubao-1-5-lite-32k")
    private String modelName;

    @Schema(description = "模型标识", example = "doubao-1-5-lite-32k-250115")
    private String model;

    @Schema(description = "所属平台", example = "DouBao")
    private String platform;

    @Schema(description = "模型状态：0 开启 1 关闭", example = "0")
    private Integer modelStatus;

    @Schema(description = "实际生效来源：CONFIG 界面配置 / ENV 环境变量 / NONE 未配置", example = "CONFIG")
    private String effectiveSource;

    @Schema(description = "实际生效的模型名（用于直接展示给运维）", example = "doubao-1-5-lite-32k")
    private String effectiveModel;

    @Schema(description = "实际生效的调用地址（已脱敏，不含 Key）", example = "https://ark.cn-beijing.volces.com/api/v3/chat/completions")
    private String effectiveBaseUrl;

}
