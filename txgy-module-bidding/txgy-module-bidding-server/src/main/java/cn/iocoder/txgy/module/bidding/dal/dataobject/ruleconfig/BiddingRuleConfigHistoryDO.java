package cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 规则变更历史（表 bidding_rule_config_history，只追加 + 哈希链）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.2（审计与追溯）；《电子证件全流程管理_数据库设计.md》v3.3 §13（哈希链防篡改）；
 * 等保 2.0 三级（日志保留 ≥3 年、DB 账号对日志表仅授 INSERT）。
 * <p>为什么单独建表而不是复用主表：主表按 rule_key+version_no 保留多版本，但"谁在何时因为什么改"属于审计事实，
 * 与配置本体生命周期不同（配置可删、审计不可删），混在一起会让审计链随配置清理而断裂。
 * <p>业务目标：任何一次阈值调整都能回答"改前是多少、改后是多少、谁改的、依据什么"。
 */
@TableName("bidding_rule_config_history")
@Data
@EqualsAndHashCode(callSuper = true)
public class BiddingRuleConfigHistoryDO extends TenantBaseDO {

    private Long id;
    private String ruleKey;
    /** 变更前版本号（新增时为空） */
    private Integer fromVersion;
    /** 变更后版本号 */
    private Integer toVersion;
    /** 变更前值（JSON 文本） */
    private String fromValue;
    /** 变更后值（JSON 文本） */
    private String toValue;
    /** 动作 code：CREATE/PUBLISH/GRAY/ROLLBACK */
    private String changeAction;
    private String changedBy;
    private LocalDateTime changedAt;
    /** 变更原因（发布时必填，用于追责与回溯） */
    private String changeReason;
    /** 本记录哈希（SHA-256） */
    private String recordHash;
    /** 上一条记录哈希（哈希链） */
    private String prevHash;
}
