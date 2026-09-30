package cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则读取审计行。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.2。
 * <p>为什么把这些字段都给出来：{@code ruleVersion} + {@code hitSource} 是举证的核心
 * （"当时用的是哪一版、哪一层的值"），{@code traceId} 用于把同一次判定中的多条读取串起来，
 * {@code bizScene} 用于按场景统计（例如"安许风险重算一共读过哪些键"）。
 * <p>业务目标：争议发生时，凭一条记录即可定位判定依据。
 */
@Data
public class RuleConfigAuditRespVO {

    @Schema(description = "审计记录 id", example = "90001")
    private Long id;

    @Schema(description = "规则键", example = "safety.abc.required")
    private String ruleKey;

    @Schema(description = "实际命中的版本号", example = "3")
    private Integer ruleVersion;

    @Schema(description = "命中来源 code：TENANT / GRAY / PLATFORM / FALLBACK", example = "TENANT")
    private String hitSource;

    @Schema(description = "业务场景", example = "SAFETY_RISK_EVAL")
    private String bizScene;

    @Schema(description = "读取人（用户 ID；SYSTEM=定时任务）", example = "1024")
    private String readBy;

    @Schema(description = "读取时刻", example = "1788668723000")
    private LocalDateTime readAt;

    @Schema(description = "链路追踪 ID", example = "a1b2c3d4e5f6")
    private String traceId;
}
