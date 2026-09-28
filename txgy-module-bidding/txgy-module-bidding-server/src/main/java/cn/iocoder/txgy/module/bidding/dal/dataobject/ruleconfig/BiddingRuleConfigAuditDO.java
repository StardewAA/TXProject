package cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 规则读取审计（表 bidding_rule_config_audit，只追加）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.2（审计与追溯）；《电子证件全流程管理_数据库设计.md》v3.3 §13；
 * 等保 2.0 三级（日志保留 ≥3 年、DB 账号对日志表仅授 INSERT）。
 *
 * <p>为什么与 {@code bidding_rule_config_history} 分开建表：history 记"**谁改了什么**"（变更事实），
 * 本表记"**谁在什么场景读到了哪一版、命中来源是哪一层**"（读取事实）。二者回答的问题不同：
 * 阈值本身没被改过，但"当时的业务判定用的是租户覆盖值还是平台默认值"同样会引发争议
 * （例：投标被判定为风险 B 而申诉，需要证明当时命中的是 TENANT 覆盖而非平台默认）。
 *
 * <p>为什么不在每次读取都写：规则读取在热路径上（缓存命中亦然），逐次落库会把一次内存读变成一次 DB 写；
 * 因此只在**业务判定点**（如安许 A/B/C 风险重算、人员风险判定）显式记录，见
 * {@code RuleConfigService#resolveWithAudit}。写入是**尽力而为**：审计失败只告警，不影响业务取到规则值。
 *
 * <p>业务目标：任何一次规则驱动的判定，都能回答"依据哪一版、来自哪一层、谁读的、什么链路"。
 */
@TableName("bidding_rule_config_audit")
@Data
@EqualsAndHashCode(callSuper = true)
public class BiddingRuleConfigAuditDO extends TenantBaseDO {

    private Long id;
    /** 规则键 */
    private String ruleKey;
    /** 实际命中的版本号 */
    private Integer ruleVersion;
    /** 命中来源 code：TENANT 租户覆盖 / GRAY 灰度 / PLATFORM 平台默认 / FALLBACK 代码兜底 */
    private String hitSource;
    /** 业务场景（如 SAFETY_RISK_EVAL，便于按场景统计与追溯） */
    private String bizScene;
    /** 读取人（用户 ID；SYSTEM 表示定时任务/系统内部） */
    private String readBy;
    /** 读取时刻 */
    private LocalDateTime readAt;
    /** 链路追踪 ID（与日志/trace 对齐，便于把一次判定的所有读取串起来） */
    private String traceId;
}
