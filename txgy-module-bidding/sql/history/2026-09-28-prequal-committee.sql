SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-28 资格审查委员会强制组建（《实施条例》第18条，国有资金项目）
--
-- 规范依据：《电子招标投标全流程说明书》§6.3 第 3 条（国有资金项目强制要求组建
--   资格审查委员会，否则禁止进入审查）；《实施条例》第18条（国有资金占控股或主导
--   的依法必招项目应组建资格审查委员会）、第37条（评标/资格审查委员会由招标人代表
--   与技术经济专家组成，5人以上单数，专家≥2/3）。
--
-- 背景（说明书 §6.6 待补项）：此前资格预审域无「资格审查委员会」实体与强制校验，
--   国有资金项目未组建委员会即可审查，违反条例第18条。本脚本补齐：
--   ① bidding_prequal_committee 资格审查委员会成员表（子表，按资格预审挂）；
--   ② fund_source 资金来源字典（bidding_dict_data 种子；项目登记 FUND_SOURCE 字段
--      由自由文本改为字典代码，01/02/03 为国有资金，供后端判定强制组建）；
--   ③ 菜单权限点 bidding:prequal:committee（挂项目台账 6771）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行。
-- 执行库：ruoyi-vue-pro（与既有 bidding_* 表同库）。
-- ============================================================================

-- ① 资格审查委员会成员表（子表）
CREATE TABLE IF NOT EXISTS `bidding_prequal_committee` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID',
  `prequal_id` bigint NOT NULL COMMENT '资格预审ID（bidding_prequal.id）',
  `member_name` varchar(64) NOT NULL COMMENT '成员姓名',
  `member_category` varchar(32) NOT NULL COMMENT '成员类别（TENDERER_REP 招标人代表 / TECH_EXPERT 技术专家 / ECON_EXPERT 经济专家）',
  `member_role` varchar(32) DEFAULT NULL COMMENT '委员会内角色（LEADER 组长 / MEMBER 成员）',
  `member_org` varchar(256) DEFAULT NULL COMMENT '所在单位',
  `member_title` varchar(128) DEFAULT NULL COMMENT '职称/职务',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_prequal` (`prequal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资格审查委员会成员表（条例第18条：国有资金项目强制组建）';

-- ② 资金来源字典（bidding_dict_data 种子；01/02/03 = 国有资金占控股或主导）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(710000000000007701,0,'fund_source','01','财政性资金','primary',b'0',1,b'1','国有资金（财政预算内/外，含转移支付）；条例第18条触发资格审查委员会强制组建','1',NOW(),'1',NOW(),b'0'),
(710000000000007702,0,'fund_source','02','国有企事业单位资金','primary',b'0',2,b'1','国有资金（国有企事业单位自有资金）；触发资格审查委员会强制组建','1',NOW(),'1',NOW(),b'0'),
(710000000000007703,0,'fund_source','03','国有控股/主导混合资金','primary',b'0',3,b'1','国有资金（国有资本占控股或主导地位的混合资金）；触发资格审查委员会强制组建','1',NOW(),'1',NOW(),b'0'),
(710000000000007704,0,'fund_source','04','非国有资金','default',b'0',4,b'1','非国有资金（民营/社会资本）','1',NOW(),'1',NOW(),b'0'),
(710000000000007705,0,'fund_source','99','其他','default',b'0',5,b'1','其他资金来源','1',NOW(),'1',NOW(),b'0');

-- ③ 菜单权限点：资格审查委员会组建（type=3 按钮，挂项目台账 6771）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006221, '资格审查委员会组建', 'bidding:prequal:committee', 3, 18, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ④ 角色授权：沿用所属页面菜单 6771（项目台账）的既有授权 ——
--    能看到「项目台账」页的角色，同时拿到这个按钮权限（按钮继承页面授权，手法同
--    2026-09-28 tender-doc-prequal-menu）。之后仍可在「角色授权」界面单独调整。
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` IN (60006221)
WHERE rm.`menu_id` = 6771 AND rm.`deleted` = b'0';

-- ========== 校验 ==========
-- 期望①：权限点存在，type=3、parent_id=6771
SELECT id, name, permission, type, sort, parent_id, deleted FROM `system_menu` WHERE id = 60006221;
-- 期望②：授权行数与页面菜单一致
SELECT (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id = 6771 AND deleted = b'0') AS page_grants,
       (SELECT COUNT(*) FROM `system_role_menu` WHERE menu_id = 60006221 AND deleted = b'0') AS button_grants;
