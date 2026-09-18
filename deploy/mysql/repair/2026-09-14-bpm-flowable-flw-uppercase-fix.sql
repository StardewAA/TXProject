-- =====================================================================
-- 修复：BPM 服务启动失败 —— Flowable 的 FLW_* 表未归一化为大写
--
-- 现象：bpm 容器启动即退出并反复重启，日志报：
--   org.flowable.common.engine.api.FlowableException:
--     Flowable database problem: Tables missing for component(s) eventregistry
--
-- 原因（已实库核对）：
--   1) 容器内 MySQL 的 lower_case_table_names = 0，表名区分大小写；
--   2) 初始化用的 0069 全库 dump 中，Flowable 的事件注册/批处理表为**小写**：
--      flw_event_deployment / flw_event_resource / flw_event_definition /
--      flw_channel_definition / flw_ru_batch / flw_ru_batch_part
--   3) 而 Flowable 8.0.0 一律按**大写** FLW_* 访问，于是判定「表缺失」；
--   4) 归一化脚本 0070-flowable-uppercase-fix.sql 里虽已包含这些表的改名语句
--      （见该文件第 74-84 行注释与语句），但按 deploy/README-docker.md 的说明，
--      0070 被排除在 init.d 之外，因此本库从未执行过归一化。
--      核心引擎表 act_* 之前已被单独修正为大写，所以只有 FLW_* 仍为小写。
--
-- 方案：与 0070 一致 —— 删除可能存在的空大写表，再把小写表改名为大写。
--
-- 幂等与安全：
--   * 每张表都以「小写表是否存在」为前置条件；执行一次后小写表不再存在，
--     再次执行将全部跳过，不会误删已归一化的大写表。
--   * 仅涉及 Flowable 的 6 张 FLW_* 表（当前均为空表），不改动任何业务数据。
--   * 若大写表已存在且含有数据，请先人工确认后再执行本脚本。
--
-- 生成日期：2026-09-14   生成人：刘洋（人员4）
-- 涉及服务：txgy-module-bpm
-- =====================================================================

USE `ruoyi-vue-pro`;

-- 通用处理：对每张表，仅当小写表存在时，先丢空的大写重复表，再改名 ----------
-- 1. flw_event_deployment -> FLW_EVENT_DEPLOYMENT
SET @has = (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flw_event_deployment');
SET @ddl = IF(@has > 0, 'DROP TABLE IF EXISTS `FLW_EVENT_DEPLOYMENT`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(@has > 0, 'RENAME TABLE `flw_event_deployment` TO `FLW_EVENT_DEPLOYMENT`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. flw_event_resource -> FLW_EVENT_RESOURCE
SET @has = (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flw_event_resource');
SET @ddl = IF(@has > 0, 'DROP TABLE IF EXISTS `FLW_EVENT_RESOURCE`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(@has > 0, 'RENAME TABLE `flw_event_resource` TO `FLW_EVENT_RESOURCE`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. flw_event_definition -> FLW_EVENT_DEFINITION
SET @has = (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flw_event_definition');
SET @ddl = IF(@has > 0, 'DROP TABLE IF EXISTS `FLW_EVENT_DEFINITION`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(@has > 0, 'RENAME TABLE `flw_event_definition` TO `FLW_EVENT_DEFINITION`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. flw_channel_definition -> FLW_CHANNEL_DEFINITION
SET @has = (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flw_channel_definition');
SET @ddl = IF(@has > 0, 'DROP TABLE IF EXISTS `FLW_CHANNEL_DEFINITION`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(@has > 0, 'RENAME TABLE `flw_channel_definition` TO `FLW_CHANNEL_DEFINITION`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. flw_ru_batch -> FLW_RU_BATCH（Flowable 批处理组件同样按大写访问）
SET @has = (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flw_ru_batch');
SET @ddl = IF(@has > 0, 'DROP TABLE IF EXISTS `FLW_RU_BATCH`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(@has > 0, 'RENAME TABLE `flw_ru_batch` TO `FLW_RU_BATCH`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. flw_ru_batch_part -> FLW_RU_BATCH_PART
SET @has = (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flw_ru_batch_part');
SET @ddl = IF(@has > 0, 'DROP TABLE IF EXISTS `FLW_RU_BATCH_PART`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(@has > 0, 'RENAME TABLE `flw_ru_batch_part` TO `FLW_RU_BATCH_PART`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 自检 -----------------------------------------------------------------
SELECT '=== FLW_* 表（应全部为大写）===' AS info;
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) LIKE '%flw%'
ORDER BY TABLE_NAME;

SELECT '=== 仍为小写的 Flowable 表（应为空）===' AS info;
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND BINARY TABLE_NAME = LOWER(TABLE_NAME)
  AND TABLE_NAME LIKE 'flw%';
