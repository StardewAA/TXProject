-- =====================================================================
-- 2026-10-02 安许风险规则表（契约《电子证照域e2e暴露问题-2026-09-21》P1 方案A 配套）
--
-- 规范依据：前端已发布 api/bidding/safetyRisk（GET /bidding/risk-rule/page、POST /create、
--   PUT /update、PUT /status、DELETE /delete），后端全无实现 → 页面规则配置区静默空态。
-- 表设计：规则实体（规则名称/触发条件/条件类型/影响范围/关联阈值键/启用禁用/命中等级/备注），
--   rule_key 可选挂钩既有 rule-config 阈值键（阈值仍然是 rule-config，本表只登记"规则"）。
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行；无 DROP/DELETE/TRUNCATE。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `safety_risk_rule` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `condition_expr` varchar(1024) DEFAULT NULL COMMENT '触发条件（自然语言或表达式；前端字段名 condition，避 MySQL 保留字）',
  `condition_type` varchar(32) DEFAULT NULL COMMENT '触发条件类型 code：THRESHOLD/EXPRESSION/NATURAL（字典 risk_rule_cond_type）',
  `scope` varchar(256) DEFAULT NULL COMMENT '影响范围（如"全部企业资质"/"安许 A/B/C"）',
  `rule_key` varchar(128) DEFAULT NULL COMMENT '关联阈值键（可选，挂 rule-config 的 ruleKey）',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '启用/禁用',
  `risk_level` varchar(16) DEFAULT NULL COMMENT '命中后产出的条目等级 code（字典 risk_level）',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_risk_rule_name` (`rule_name`),
  KEY `idx_risk_rule_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安许风险规则（/bidding/risk-rule/*）';
