package cn.iocoder.txgy.module.bidding.controller.admin.ruleconfig.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则变更历史（审计展示）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.2（所有规则变更必须有不可篡改审计日志）。
 * <p>为什么把 recordHash/prevHash 返给前端：审计页需要能让审查者看到"链式指纹"，
 * 必要时可与独立审计库的根哈希比对，证明记录未被篡改（等保三级要求）。
 * <p>业务目标：配置变更可追溯到人、时间、原因、前后值，并具备防篡改证据。
 */
@Schema(description = "管理后台 - 规则变更历史")
@Data
public class RuleConfigHistoryRespVO {

    @Schema(description = "历史主键")
    private Long id;

    @Schema(description = "规则键")
    private String ruleKey;

    @Schema(description = "变更前版本号")
    private Integer fromVersion;

    @Schema(description = "变更后版本号")
    private Integer toVersion;

    @Schema(description = "变更前值（JSON 文本）")
    private String fromValue;

    @Schema(description = "变更后值（JSON 文本）")
    private String toValue;

    @Schema(description = "动作：CREATE/PUBLISH/GRAY/ROLLBACK")
    private String changeAction;

    @Schema(description = "变更人")
    private String changedBy;

    @Schema(description = "变更时刻")
    private LocalDateTime changedAt;

    @Schema(description = "变更原因")
    private String changeReason;

    @Schema(description = "本记录哈希（SHA-256）")
    private String recordHash;

    @Schema(description = "上一条记录哈希（哈希链）")
    private String prevHash;
}
