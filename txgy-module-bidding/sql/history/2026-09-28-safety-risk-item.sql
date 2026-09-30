-- =====================================================================
-- 2026-09-28 安许风险条目工单表（五页改造缺接口契约 Q18/Q19 配套）
--
-- 规范依据：《电子资质域-待决策与残留事项清单》§4.15/契约 Q18（风险条目数据模型：等级/描述/关联证照/
--           影响主体/责任人/状态/指派）+ Q19（风险大盘 high/medium/low/handled/total）。
-- 设计遵循（Q20 口径裁定）："重新扫描风险"复用既有 POST /bidding/safety-permit/risk/recheck——
--   evaluateOne 落快照时按档位自动生成/点亮工单（同 permit 未闭环工单不叠加），不另设扫描入口；
--   责任人可指派、处理人乐观锁 CAS（version），与模块其他工单/台账同范式。
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行；无 DROP/DELETE/TRUNCATE。
-- 执行库：ruoyi-vue-pro。
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `safety_risk_item` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `ent_id` bigint NOT NULL COMMENT '影响企业主体 id（enterprise 主体档案）',
  `permit_id` bigint DEFAULT NULL COMMENT '关联安许证 id（qualification_safety_permit；空=非安许来源）',
  `risk_level` varchar(16) NOT NULL COMMENT '风险等级 code：LOW/MEDIUM/HIGH/MAJOR（字典 risk_level）',
  `risk_desc` varchar(512) NOT NULL COMMENT '风险描述',
  `cert_ref` varchar(256) DEFAULT NULL COMMENT '关联证照/证书名称快照（如安许证编号+企业名）',
  `assignee_id` bigint DEFAULT NULL COMMENT '责任人 id（指派后回填）',
  `assignee_name` varchar(64) DEFAULT NULL COMMENT '责任人姓名快照（展示免联查）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '工单状态 code：PENDING/ASSIGNED/HANDLING/HANDLED/IGNORED（字典 risk_item_status）',
  `found_at` datetime NOT NULL COMMENT '发现时间（=扫描落库时刻）',
  `handled_at` datetime DEFAULT NULL COMMENT '处理完成时间',
  `handle_remark` varchar(512) DEFAULT NULL COMMENT '处理/指派备注',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（0 未删除 1 已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_item_ent_status` (`tenant_id`, `ent_id`, `status`),
  KEY `idx_item_level_status` (`tenant_id`, `risk_level`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安许风险条目工单（契约 Q18/Q19）';

-- =====================================================================
-- 已并入 master-credential-sync.sql（字典行 + 菜单权限点同步登记）
-- =====================================================================

-- ① 字典：工单状态（用户铁律：状态码/枚举动态配置，严禁硬编码）
INSERT IGNORE INTO `bidding_dict_data`
(`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
-- id 用 2901~905：2801~803 已被 material_result 字典占用（2026-09-19-cert-dict-register.sql），撞号会被 INSERT IGNORE 静默跳过
-- 字典名与取值对齐前端已发布类型（safetyRisk/index.ts 的 RiskStatus：PENDING/ASSIGNED/HANDLING/HANDLED/IGNORED），
-- 后端 SafetyRiskItemStatusEnum 同源；改名自 2026-09-28 首版 safety_risk_item_status（三态），见通知文件第 27 条。
(710000000000002901,0,'risk_item_status','PENDING','待处理','warning',b'0',1,b'1','扫描生成，尚未指派','1',NOW(),'1',NOW(),b'0'),
(710000000000002902,0,'risk_item_status','ASSIGNED','已指派','info',b'0',2,b'1','已指定责任人，尚未开始处理','1',NOW(),'1',NOW(),b'0'),
(710000000000002903,0,'risk_item_status','HANDLING','处理中','processing',b'0',3,b'1','责任人处理中','1',NOW(),'1',NOW(),b'0'),
(710000000000002904,0,'risk_item_status','HANDLED','已处理','success',b'0',4,b'1','终态：处理闭环（扫描确认风险解除可自动关闭）','1',NOW(),'1',NOW(),b'0'),
(710000000000002905,0,'risk_item_status','IGNORED','已忽略','default',b'0',5,b'1','终态：经确认无需处理','1',NOW(),'1',NOW(),b'0');

-- ② 菜单权限点（挂 60005500 安许风险联动页；角色授权口径同 60005215 先例：超管默认全放行，其他角色按需授权）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- id 用 60005509~511：60005505/506 已被规则配置历史占用（2026-09-21-bidding-rule-config-menu.sql），撞号会被 INSERT IGNORE 静默跳过
(60005509, '风险条目查询', 'bidding:safety-risk:query', 3, 31, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005510, '风险条目指派', 'bidding:safety-risk:assign', 3, 32, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60005511, '风险条目处理', 'bidding:safety-risk:handle', 3, 33, 60005500, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
