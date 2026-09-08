-- ============================================================
-- 修复：training_operation_log 缺少 updater / update_time 标准审计列
-- 背景：框架 BaseDO 映射 updater/update_time，该表建表时遗漏，
--       导致对操作日志的查询报 Unknown column 'update_time'。
-- 影响：资源中心操作日志查询、数据统计看板（模块九）。
-- 日期：2026-09-04
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE `training_operation_log`
    ADD COLUMN `updater`     varchar(64) DEFAULT ''              COMMENT '更新者'   AFTER `creator`,
    ADD COLUMN `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`;
