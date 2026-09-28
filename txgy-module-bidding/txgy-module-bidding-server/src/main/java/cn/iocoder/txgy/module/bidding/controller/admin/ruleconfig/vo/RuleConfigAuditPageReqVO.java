package cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 规则读取审计分页查询入参。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.2（审计与追溯）。
 * <p>为什么这样写：审计查询是"事后追责/争议举证"场景，筛选项围绕举证需要给出：
 * 键（哪条规则）、来源（哪一层命中的）、场景（哪次判定）、读取人、时间区间；全部可空以便先看全量。
 * <p>业务目标：一次查询即可还原"某次判定读了什么、命中了哪一层"。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RuleConfigAuditPageReqVO extends PageParam {

    @Schema(description = "规则键", example = "safety.abc.required")
    private String ruleKey;

    @Schema(description = "命中来源：TENANT / GRAY / PLATFORM / FALLBACK", example = "PLATFORM")
    private String hitSource;

    @Schema(description = "业务场景（如 SAFETY_RISK_EVAL / PERSONNEL_RISK）", example = "SAFETY_RISK_EVAL")
    private String bizScene;

    @Schema(description = "读取人（用户 ID 或 SYSTEM）", example = "1024")
    private String readBy;

    @Schema(description = "读取时刻起（含）", example = "2026-09-01 00:00:00")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAtFrom;

    @Schema(description = "读取时刻止（含）", example = "2026-09-30 23:59:59")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAtTo;
}
