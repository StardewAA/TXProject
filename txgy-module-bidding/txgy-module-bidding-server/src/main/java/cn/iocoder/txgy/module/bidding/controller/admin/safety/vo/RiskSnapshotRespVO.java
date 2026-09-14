package cn.iocoder.txgy.module.bidding.controller.admin.safety.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安许风险快照（历史追溯）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.1(c)/§1.3（history 接口），§8.2（审计与哈希链）。
 * <p>为什么把 recordHash 返给前端：审计需要让审查者看到链式指纹，必要时与独立审计库根哈希比对以证明未被篡改。
 * <p>业务目标：企业可自证"当时判定依据"，监管可复核。
 */
@Schema(description = "管理后台 - 安许风险快照")
@Data
public class RiskSnapshotRespVO {

    @Schema(description = "快照主键")
    private Long id;

    @Schema(description = "本次风险档")
    private String riskFlag;

    @Schema(description = "上次风险档（识别升降级）")
    private String prevRiskFlag;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "A/B/C 类人数")
    private Integer aCount;
    private Integer bCount;
    private Integer cCount;

    @Schema(description = "是否处于宽限期")
    private Boolean graceApplied;

    @Schema(description = "命中的规则（JSON）")
    private String hitRulesJson;

    @Schema(description = "配备要求（JSON，留证）")
    private String minRequiredJson;

    @Schema(description = "规则版本")
    private String ruleVersion;

    @Schema(description = "评估批次号")
    private String batchNo;

    @Schema(description = "评估时刻")
    private LocalDateTime evaluatedAt;

    @Schema(description = "本记录哈希（SHA-256）")
    private String recordHash;

    @Schema(description = "上一条哈希（哈希链）")
    private String prevHash;
}
