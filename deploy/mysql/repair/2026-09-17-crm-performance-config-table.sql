-- ============================================================================
-- CRM 补齐缺失表：crm_performance_config（业绩目标设置）
-- 日期：2026-09-17
--
-- 背景：
--   CrmPerformanceConfigDO 声明了 @TableName("crm_performance_config")，
--   但仓库中不存在该表的建表 SQL（全仓 *.sql 检索无匹配），本库也没有该表，
--   导致：
--     GET /admin-api/crm/performance-config/*  → 501
--       「[CRM 系统 txgy-module-crm - 表结构未导入]」
--     GET /admin-api/crm/statistics-performance-target/* → 同上（该统计同样读此表）
--   前端「系统配置 → 业绩目标设置」「数据统计 → 业绩达成」两个页面因此不可用。
--
-- 字段依据：txgy-module-crm-server/.../dal/dataobject/performance/CrmPerformanceConfigDO.java
--   biz_type    目标类型   {@link cn.iocoder.txgy.module.crm.enums.performance.CrmPerformanceConfigBizTypeEnum}
--                         5=销售目标（合同） / 7=回款目标（回款）
--   object_id   目标对象编号（部门编号或用户编号）
--   object_type 目标对象类型 {@link CrmPerformanceConfigObjectTypeEnum} 2=部门 / 3=员工
--   year + 12 个月的目标金额
--   审计字段沿用框架 BaseDO 约定（与 crm_contract_config 等既有表完全一致）
--
-- 处理原则：
--   1. CREATE TABLE IF NOT EXISTS，可重复执行；
--   2. 金额统一 decimal(24,6)，与 crm_business.total_price 等既有列保持一致；
--   3. 不预置任何演示数据（业绩目标属于业务配置，由页面录入）。
-- ============================================================================

USE `ruoyi-vue-pro`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `crm_performance_config`
(
    `id`                      bigint         NOT NULL AUTO_INCREMENT COMMENT '编号',
    `biz_type`                tinyint        DEFAULT NULL COMMENT '目标类型：5=销售目标（合同），7=回款目标',
    `object_id`               bigint         DEFAULT NULL COMMENT '目标对象编号（部门编号或用户编号）',
    `object_type`             tinyint        DEFAULT NULL COMMENT '目标对象类型：2=部门，3=员工',
    `year`                    int            DEFAULT NULL COMMENT '年份',
    `year_target_price`       decimal(24, 6) DEFAULT NULL COMMENT '年度目标金额',
    `january_target_price`    decimal(24, 6) DEFAULT NULL COMMENT '一月目标金额',
    `february_target_price`   decimal(24, 6) DEFAULT NULL COMMENT '二月目标金额',
    `march_target_price`      decimal(24, 6) DEFAULT NULL COMMENT '三月目标金额',
    `april_target_price`      decimal(24, 6) DEFAULT NULL COMMENT '四月目标金额',
    `may_target_price`        decimal(24, 6) DEFAULT NULL COMMENT '五月目标金额',
    `june_target_price`       decimal(24, 6) DEFAULT NULL COMMENT '六月目标金额',
    `july_target_price`       decimal(24, 6) DEFAULT NULL COMMENT '七月目标金额',
    `august_target_price`     decimal(24, 6) DEFAULT NULL COMMENT '八月目标金额',
    `september_target_price`  decimal(24, 6) DEFAULT NULL COMMENT '九月目标金额',
    `october_target_price`    decimal(24, 6) DEFAULT NULL COMMENT '十月目标金额',
    `november_target_price`   decimal(24, 6) DEFAULT NULL COMMENT '十一月目标金额',
    `december_target_price`   decimal(24, 6) DEFAULT NULL COMMENT '十二月目标金额',
    `creator`                 varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
    `create_time`             datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                 varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
    `update_time`             datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                 bit(1)         NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`               bigint         NOT NULL DEFAULT '0' COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_biz_object_year` (`biz_type`, `object_type`, `object_id`, `year`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='CRM 业绩目标配置表';

-- ---------------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------------
SELECT '=== 校验：表是否就位 ===' AS section;
SELECT COUNT(*) AS crm_performance_config_exists
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'crm_performance_config';

SELECT '=== 校验：字段清单 ===' AS section;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'crm_performance_config'
ORDER BY ORDINAL_POSITION;
