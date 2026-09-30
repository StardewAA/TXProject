package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 风险规则新增/修改入参（前端 RuleSaveReq 同形；update 必传 id + version）。 */
@Schema(description = "管理后台 - 安许风险规则 Save Request")
@Data
public class SafetyRiskRuleSaveReqVO {

    @Schema(description = "主键（修改必传）", example = "96001")
    private Long id;

    @Schema(description = "乐观锁版本（修改必传）", example = "0")
    private Integer version;

    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "安许 B 类人员配备不足")
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128")
    private String ruleName;

    @Schema(description = "触发条件（自然语言或表达式）", example = "B 类持证人数 < 配置阈值")
    @Size(max = 1024, message = "触发条件长度不能超过 1024")
    private String condition;

    @Schema(description = "触发条件类型 code：THRESHOLD/EXPRESSION/NATURAL", example = "THRESHOLD")
    private String conditionType;

    @Schema(description = "影响范围", example = "安许 A/B/C")
    @Size(max = 256, message = "影响范围长度不能超过 256")
    private String scope;

    @Schema(description = "关联阈值键（可选）", example = "safety.abc.required")
    @Size(max = 128, message = "关联阈值键长度不能超过 128")
    private String ruleKey;

    @Schema(description = "启用/禁用（空=默认启用）", example = "true")
    private Boolean enabled;

    @Schema(description = "命中后产出的条目等级 code", example = "HIGH")
    private String riskLevel;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512")
    private String remark;
}
