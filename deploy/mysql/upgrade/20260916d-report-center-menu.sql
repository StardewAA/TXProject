-- =====================================================================
-- 2026-09-16 报表中心菜单与权限
-- 背景：数据集管理面向管理员（SQL/配置），业务人员需要的是「选报表 → 一键导出」。
--       本脚本在 MES 系统下新增「报表中心」入口，权限独立于数据集配置权限。
-- 菜单：6900 报表中心（MES 系统 5100 下）
--       6901 查询报表 / 6902 导出报表
-- 说明：幂等，可重复执行。
-- =====================================================================

SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (6900, '报表中心', 'report:center:query', 2, 91, 5100, 'report-center', 'lucide:file-spreadsheet', 'report/center/index', 'MesReportCenter', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
                        `type` = VALUES(`type`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
                        `icon` = VALUES(`icon`), `component` = VALUES(`component`),
                        `component_name` = VALUES(`component_name`), `sort` = VALUES(`sort`),
                        `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (6901, '查询报表', 'report:center:query', 3, 1, 6900, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
       (6902, '导出报表', 'report:center:export', 3, 2, 6900, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`),
                        `type` = VALUES(`type`), `parent_id` = VALUES(`parent_id`), `sort` = VALUES(`sort`),
                        `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0';

UPDATE `system_menu`
SET `type` = 3, `updater` = 'admin', `update_time` = NOW()
WHERE `id` IN (6901, 6902) AND `type` <> 3;

COMMIT;
