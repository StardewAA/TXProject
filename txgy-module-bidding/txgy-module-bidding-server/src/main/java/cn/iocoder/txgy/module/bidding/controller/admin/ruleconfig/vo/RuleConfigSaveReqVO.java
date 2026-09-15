package cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则配置保存（草稿）/ 发布入参。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（save-draft / publish / gray）；《招投标模块开发规范》§4.5（写接口幂等 + 乐观锁）。
 * <p>为什么"变更原因"必填：配置直接改变线上判定结果（如把预警窗口从 90 天调到 7 天），
 * 没有原因的变更在事后审计时无法判断是误操作还是业务决定，等于审计链断裂。
 * <p>业务目标：每一次阈值调整都有据可查，可灰度、可回滚。
 */
@Schema(description = "管理后台 - 规则配置保存入参")
@Data
public class RuleConfigSaveReqVO {

    @Schema(description = "规则键", requiredMode = Schema.RequiredMode.REQUIRED, example = "cert.expiring.window.days")
    @NotBlank(message = "规则键不能为空")
    private String ruleKey;

    @Schema(description = "配置值（JSON 文本；标量写 60 / true / “SMX”，数组写 [90,60,30,7]）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "60")
    @NotBlank(message = "配置值不能为空")
    private String configValue;

    @Schema(description = "值类型：INT/DECIMAL/BOOL/STRING/JSON", requiredMode = Schema.RequiredMode.REQUIRED, example = "INT")
    @NotBlank(message = "值类型不能为空")
    private String valueType;

    @Schema(description = "分组 code：CERT/ALERT/PERSONNEL/…", example = "CERT")
    private String ruleGroup;

    @Schema(description = "人话说明")
    private String description;

    @Schema(description = "法规/文档出处")
    private String regulationRef;

    @Schema(description = "允许边界 JSON，如 “min”:1 与 “max”:365")
    private String legalFloor;

    @Schema(description = "变更原因（必填，写入审计）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "变更原因不能为空")
    private String changeReason;

    @Schema(description = "目标状态：DRAFT 存草稿 / PUBLISHED 发布 / GRAY 灰度", example = "PUBLISHED")
    private String status;

    @Schema(description = "灰度范围 JSON：tenantIds 数组 或 percent 百分比（status=GRAY 时必填）")
    private String grayScope;

    @Schema(description = "生效开始（空=立即）")
    private LocalDateTime effectiveFrom;

    @Schema(description = "生效结束（空=长期）")
    private LocalDateTime effectiveTo;

    @Schema(description = "乐观锁版本（修改既有行时回传）")
    private Integer version;
}
