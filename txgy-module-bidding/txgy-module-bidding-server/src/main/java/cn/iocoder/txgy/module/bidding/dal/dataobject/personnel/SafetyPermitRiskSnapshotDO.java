package cn.iocoder.txgy.module.bidding.dal.dataobject.personnel;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 安许风险快照（表 safety_permit_risk_snapshot，只追加 + 哈希链）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.1(c)/§8.2；《电子证件全流程管理_数据库设计.md》v3.3 §13（哈希链）；
 * 等保 2.0 三级（日志 ≥3 年、日志表仅授 INSERT）。
 * <p>为什么每次评估都要落快照而不是只更新主表：① 风险档位会随人员流动变化，出了争议必须能回答
 * "当时为什么是红/黄"——hit_rules_json + rule_version 让判定可复算；② 只追加 + 哈希链满足防篡改要求，
 * 主表 UPDATE 无法承担审计职责。
 * <p>业务目标：风险判定可复算、可追溯、不可抵赖。
 */
@TableName("safety_permit_risk_snapshot")
@Data
@EqualsAndHashCode(callSuper = true)
public class SafetyPermitRiskSnapshotDO extends TenantBaseDO {

    private Long id;
    private Long permitId;
    private Long entId;
    private String riskFlag;
    private String prevRiskFlag;
    private String riskLevel;
    private Integer aCount;
    private Integer bCount;
    private Integer cCount;
    /** 本次判定所用配备要求（JSON 留证） */
    private String minRequiredJson;
    private Boolean graceApplied;
    /** 命中的规则键与取值（JSON，可复算） */
    private String hitRulesJson;
    private String ruleVersion;
    private String batchNo;
    private LocalDateTime evaluatedAt;
    private String recordHash;
    private String prevHash;
}
