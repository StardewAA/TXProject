-- =============================================================
-- 补齐缺失表：crm_owner_record（CRM 负责人变更记录表）
--
-- 背景：
--   客户公海「领取 / 分配」时，后端会写入一条负责人变更记录（crm_owner_record）。
--   但部分环境（按 deploy/mysql/init 初始化的库）缺少这张表，导致：
--     INSERT INTO crm_owner_record ... 报 1146 Table doesn't exist
--     → 被 GlobalExceptionHandler 兜底成
--       501「[CRM 系统 txgy-module-crm - 表结构未导入]」
--     → 由于同一事务回滚，表现为「客户公海领取不了、也分配不了」。
--
-- 影响的功能：
--   1. 客户公海：领取、分配
--   2. 各类负责人转移：客户 / 线索 / 商机 / 合同（转移时也会写该表）
--   3. 客户放入公海（负责人置空）
--
-- 适用库：ruoyi-vue-pro
-- 说明：本脚本幂等（CREATE TABLE IF NOT EXISTS），可重复执行。
-- =============================================================

USE `ruoyi-vue-pro`;

CREATE TABLE IF NOT EXISTS `crm_owner_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` tinyint NOT NULL COMMENT 'CRM 业务类型（见 CrmBizTypeEnum）',
  `biz_id` bigint NOT NULL COMMENT 'CRM 业务编号',
  `pre_owner_user_id` bigint DEFAULT NULL COMMENT '变更前负责人',
  `post_owner_user_id` bigint DEFAULT NULL COMMENT '变更后负责人',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_biz` (`biz_type`, `biz_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 负责人变更记录表';

-- 校验
SHOW COLUMNS FROM `crm_owner_record`;
