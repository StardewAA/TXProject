package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风险规则出参。**与前端 api/bidding/safetyRisk 的 {@code SafetyRiskApi.Rule} 字段逐字对齐**
 * （契约《电子证照域e2e暴露问题-2026-09-21》P1 方案 A）。
 */
@Schema(description = "管理后台 - 安许风险规则 Response")
@Data
public class SafetyRiskRuleRespVO {

    @Schema(description = "主键", example = "96001")
    private Long id;

    @Schema(description = "规则名称", example = "安许 B 类人员配备不足")
    private String ruleName;

    @Schema(description = "触发条件（自然语言或表达式）", example = "B 类持证人数 < 配置阈值")
    private String condition;

    @Schema(description = "触发条件类型 code：THRESHOLD/EXPRESSION/NATURAL")
    private String conditionType;

    @Schema(description = "影响范围", example = "安许 A/B/C")
    private String scope;

    @Schema(description = "关联阈值键（可选）", example = "safety.abc.required")
    private String ruleKey;

    @Schema(description = "启用/禁用", example = "true")
    private Boolean enabled;

    @Schema(description = "命中后产出的条目等级 code：LOW/MEDIUM/HIGH/MAJOR")
    private String riskLevel;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "乐观锁版本（update 必回传）", example = "0")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
