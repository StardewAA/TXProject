package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 安许整改宽限登记入参。
 *
 * <p>规范出处：用户业务口径"法定代表人变更导致仅剩 1 名 A 证，给 180 天宽限；宽限规则需支持动态配置"；
 * 《电子资质七大模块技术方案.md》§1.3（POST /bidding/safety-permit/grace）。
 * <p>为什么原因必填且须在白名单内：宽限期内企业仍可投标，若不限制原因，宽限会变成逃避整改的后门；
 * days 允许小于配置上限但不得超出（超出即拒），避免"要 3 年宽限"这类操作。
 * <p>业务目标：宽限可给、可查、可收回。
 */
@Schema(description = "管理后台 - 安许整改宽限登记入参")
@Data
public class SafetyGraceRegisterReqVO {

    @Schema(description = "安许证主键（与 entId 二选一，优先 permitId）", example = "88")
    private Long permitId;

    @Schema(description = "企业主体（未指定 permitId 时按其安许证处理）", example = "1")
    private Long entId;

    @Schema(description = "宽限原因 code（须在 safety.abc.grace.reason.whitelist 内）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "LEGAL_PERSON_CHANGE")
    @NotBlank(message = "宽限原因不能为空")
    private String reason;

    @Schema(description = "宽限依据（变更单号/决定书号，供核查真实性）", example = "法定代表人变更登记回执 2026-0001")
    private String sourceRef;

    @Schema(description = "宽限天数（留空=取配置默认值，如 180）", example = "180")
    private Integer days;

    @Schema(description = "备注")
    private String remark;
}
