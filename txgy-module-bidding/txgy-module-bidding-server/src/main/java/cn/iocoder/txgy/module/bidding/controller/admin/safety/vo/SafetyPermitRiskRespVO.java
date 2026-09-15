package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安许风险状态（招投标 / 施工许可 / 现场监督检查**统一读取**的响应）。
 *
 * <p>规范出处：用户业务口径"联动场景：招投标、施工许可、现场监督检查读取同一风险状态"；
 * 《电子资质七大模块技术方案.md》§1.3（GET /bidding/safety-permit/risk）。
 * <p>为什么三类业务共用一个接口：若各自算一次，人员流动期间会出现"招投标拦住、现场检查放行"的矛盾结论，
 * 争议时无法解释；统一取值保证任何入口看到的都是同一时刻的同一份判定。
 * <p>业务目标：风险状态单一事实来源，且附带可复算依据（hitRules + ruleVersion）。
 */
@Schema(description = "管理后台 - 安许风险状态")
@Data
public class SafetyPermitRiskRespVO {

    @Schema(description = "安许证主键", example = "88")
    private Long permitId;

    @Schema(description = "企业主体", example = "1")
    private Long entId;

    @Schema(description = "风险档 code：NORMAL/YELLOW/RED/INVALID/FREQUENT；null=未评估", example = "RED")
    private String riskFlag;

    @Schema(description = "风险等级 code：LOW/MEDIUM/HIGH/MAJOR（供周检 Job 取提前量）", example = "HIGH")
    private String riskLevel;

    @Schema(description = "A 类（企业主要负责人）有效人数", example = "1")
    private Integer aCount;

    @Schema(description = "B 类（项目负责人）有效人数", example = "0")
    private Integer bCount;

    @Schema(description = "C 类（专职安全生产管理人员）有效人数", example = "2")
    private Integer cCount;

    @Schema(description = "宽限截止（null=无宽限；宽限期内风险降一档）")
    private LocalDateTime graceExpireAt;

    @Schema(description = "宽限原因 code")
    private String graceReason;

    @Schema(description = "是否处于宽限期", example = "false")
    private Boolean graceApplied;

    @Schema(description = "是否阻断业务（依据配置 safety.abc.block.flags 判定，前端据此决定是否禁用提交）", example = "true")
    private Boolean blocked;

    @Schema(description = "命中的规则与取值（JSON，可复算判定过程）")
    private String hitRules;

    @Schema(description = "规则版本", example = "1")
    private String ruleVersion;

    @Schema(description = "最近评估时刻")
    private LocalDateTime riskEvaluatedAt;
}
