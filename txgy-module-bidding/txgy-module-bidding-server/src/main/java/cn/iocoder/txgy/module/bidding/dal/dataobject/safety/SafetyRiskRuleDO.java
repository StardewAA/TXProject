package cn.iocoder.txgy.module.bidding.dal.dataobject.safety;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 安许风险规则实体。表：safety_risk_rule（DDL sql/history/2026-10-02-safety-risk-rule.sql）。
 *
 * <p>规范出处：契约《电子证照域e2e暴露问题-2026-09-21》P1 方案 A——前端 api/bidding/safetyRisk
 * 的「规则配置区」（规则名称/触发条件/影响范围/启用禁用）需要规则实体 CRUD，既有 rule-config 只是阈值键值。
 * <p>设计遵循/针对：① 列名用 condition_expr（condition 是 MySQL 保留字），出参字段名保持前端契约的 condition
 * （{@link TableField} 映射）；② ruleKey 可选挂钩 rule-config 的 ruleKey，阈值本身仍归 rule-config，
 * 本表不复制阈值语义；③ version 乐观锁（前端 update 必传）。
 */
@TableName("safety_risk_rule")
@Data
@EqualsAndHashCode(callSuper = true)
public class SafetyRiskRuleDO extends TenantBaseDO {

    /**
     * 主键（雪花 ID）。
     *
     * <p>为什么显式 {@code @TableId(ASSIGN_ID)}：框架 application.yaml 配 {@code id-type: NONE}
     * （"智能"模式按数据源类型适配），本地 MySQL 库的主键列均无 AUTO_INCREMENT——
     * 不加注解时 MP 判定为 DB 自增、INSERT 不带 id → SQLException "Field 'id' doesn't have a default value"。
     * 显式声明让 MP 走雪花生成（与既有 bidding_* 表同口径）。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 规则名称 */
    private String ruleName;
    /** 触发条件（自然语言或表达式）。属性名 conditionExpr → 列名 condition_expr（避 MySQL 保留字 condition；
     * 曾用 @TableField 显式映射但未生效，改由驼峰自动映射，见类头）。 */
    private String conditionExpr;
    /** 触发条件类型 code：THRESHOLD/EXPRESSION/NATURAL（字典 risk_rule_cond_type） */
    private String conditionType;
    /** 影响范围（如"全部企业资质"/"安许 A/B/C"） */
    private String scope;
    /** 关联阈值键（可选，挂 rule-config 的 ruleKey） */
    private String ruleKey;
    /** 启用/禁用 */
    private Boolean enabled;
    /** 命中后产出的条目等级 code（字典 risk_level） */
    private String riskLevel;
    /** 备注 */
    private String remark;
    /** 乐观锁版本 */
    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;
}
