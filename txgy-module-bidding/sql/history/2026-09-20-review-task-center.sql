-- =====================================================================
-- 平台审查任务中心：bidding_review_task（契约《后端变更请求-平台审查任务中心-2026-09-20》）
-- =====================================================================
-- 目标流程：企业录入证照/资质 → 后端自动生成审查任务 → 超管在平台审查中心统一审查（通过/驳回）
--           → 结果与原因回传租户（只读查看本企业任务）。
-- 口径（契约 §3）：PENDING → APPROVED / REJECTED（驳回 reason 必填留痕）；驳回后可修正重新提交，
--           产生**新任务**、不覆盖原任务（历史可追溯）；通过后该证照/资质方可被其他接口引用。
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE。无 DROP/DELETE。
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `bidding_review_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  -- 规范出处：平台 BaseDO/TenantBaseDO 约定（同库所有业务表均带 tenant_id，如 bidding_certificate、bidding_enterprise_qualification）。
  -- 为什么这样写（设计遵循）：ReviewTaskDO 继承 TenantBaseDO，MyBatis-Plus 租户插件会自动追加 `tenant_id = ?` 条件；
  --   若表里没有该列，任何查询/写入都会抛 Unknown column 'tenant_id' in 'where clause'（实测 500）。
  -- 业务目标：审查任务必须按租户隔离——租户只能看自己提交的任务，平台超管才跨租户审查。
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型：QUALIFICATION 企业资质 / CERTIFICATE 证照 / PERSON_CERT 员工证书（字典 review_biz_type）',
  `biz_id` bigint NOT NULL COMMENT '关联业务主键（资质 id / 证照 id）',
  `ent_id` bigint NOT NULL COMMENT '提交企业 id（租户隔离维度）',
  `ent_name` varchar(128) DEFAULT NULL COMMENT '提交企业名称（快照）',
  `ent_uscc` varchar(32) DEFAULT NULL COMMENT '提交企业统一社会信用代码（快照）',
  `ref_no` varchar(64) DEFAULT NULL COMMENT '关联证书编号/资质证书编号（快照）',
  `submit_time` datetime NOT NULL COMMENT '任务产生（企业录入）时间',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待审查 / APPROVED 已通过 / REJECTED 已驳回（字典 review_task_status）',
  `reviewer` varchar(64) DEFAULT NULL COMMENT '审查人（通过/驳回后回填）',
  `review_time` datetime DEFAULT NULL COMMENT '审查时间（通过/驳回后回填）',
  `reason` varchar(512) DEFAULT NULL COMMENT '驳回原因（REJECTED 必填，租户可见）',
  `remark` varchar(512) DEFAULT NULL COMMENT '提交时的备注',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁（通过/驳回必须回传）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  -- 租户列打头：租户插件的 where 条件始终带 tenant_id，放最左列才能命中索引
  KEY `idx_review_tenant_status` (`tenant_id`, `status`),
  KEY `idx_review_ent_status` (`ent_id`, `status`),
  KEY `idx_review_status` (`status`),
  KEY `idx_review_submit_time` (`submit_time`),
  KEY `idx_review_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台审查任务（企业录入 → 超管审查）';

-- 字典：review_task_status / review_biz_type（前端角标与筛选依赖中文名与配色，不得硬编码）
--
-- ⚠️ 主键号段（2026-09-20 修正，真实事故）：
--   bidding_dict_data.id 由脚本**手工指派**（该表主键非自增，见 check-package-invariants ⑥），
--   号段按"域"划分：...0030xx 员工状态 / ...0031xx 社保 / ...0032xx 性别 / ...0033xx 学历 /
--   ...0034xx 资质序列 / ...0035xx 用工形式 / ...0036xx 备案状态 / ...0037xx 主体授权 /
--   ...0038xx 主体类型 / ...0039xx 溯源来源 / ...0040xx/0041xx/0042xx/0044xx 公告与状态 / ...0046xx 审查任务状态。
--   （选段过程本身就是这条注释存在的理由：本脚本连撞三次 —— 初版 ...0030xx 撞 personnel_status、
--     改 ...0040xx 撞 notice_external_status、再改 ...0045xx 撞 verify_channel；每次都是"看起来挑空了"。
--     最终用 `node sql/tools/check-seed-id-collision.js --list` 按同口径全量扫描才定到 ...0046xx。
--     结论：**手工指派主键的种子，选段必须用工具扫描，不能靠眼看**。）
--   本脚本初版误用 ...0030xx —— 与既有 personnel_status 冲突，而 INSERT IGNORE **不报错、只跳过**，
--   于是库里"看起来执行成功"却根本没有 review_task_status 三行 ⇒ /bidding/meta/dicts 报
--   1060108008「字典类型不支持」⇒ 审查页状态下拉与角标拿不到中文。
--   教训：手工指派主键的种子数据，新增前**必须先查号段占用**；INSERT IGNORE 的静默跳过
--   要靠"执行后回查行数"而不是"命令没报错"来确认。
--   ⚠️ 号段复查（2026-09-21，契约 §8 隐患修复）：初版把 review_biz_type 也放在 ...0030xx
--   （003011~003013，与 personnel_status 同段），当时只修了 review_task_status 而漏了它——
--   同段未来新增字典会 INSERT IGNORE 静默丢行。本次把 review_biz_type 迁到 ...004604~004606
--   （审查任务域 0046xx 空闲段），已执行库由配套迁移脚本 UPDATE id（见 sql/history/
--   2026-09-21-review-biz-type-renumber.sql）。
INSERT IGNORE INTO `bidding_dict_data`
  (`id`, `tenant_id`, `dict_type`, `dict_value`, `dict_label`, `color_type`, `terminal`, `sort`, `enabled`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
  (710000000000004601, 0, 'review_task_status', 'PENDING', '待审查', 'warning', b'0', 1, b'1', '审查任务状态', '1', NOW(), '1', NOW(), b'0'),
  (710000000000004602, 0, 'review_task_status', 'APPROVED', '已通过', 'success', b'0', 2, b'1', '审查任务状态', '1', NOW(), '1', NOW(), b'0'),
  (710000000000004603, 0, 'review_task_status', 'REJECTED', '已驳回', 'error', b'0', 3, b'1', '审查任务状态', '1', NOW(), '1', NOW(), b'0'),
  (710000000000004604, 0, 'review_biz_type', 'QUALIFICATION', '企业资质', 'info', b'0', 1, b'1', '审查业务类型', '1', NOW(), '1', NOW(), b'0'),
  (710000000000004605, 0, 'review_biz_type', 'CERTIFICATE', '证照', 'info', b'0', 2, b'1', '审查业务类型', '1', NOW(), '1', NOW(), b'0'),
  (710000000000004606, 0, 'review_biz_type', 'PERSON_CERT', '员工证书', 'info', b'0', 3, b'1', '审查业务类型', '1', NOW(), '1', NOW(), b'0');
