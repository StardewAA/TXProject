-- 2026-09-15 课程管理子菜单更名：「课程名称」→「课程列表」
-- 背景：页面内容为课程的列表维护（增删改查/版本/媒资绑定），「课程列表」更贴切
SET NAMES utf8mb4;

UPDATE `system_menu`
SET `name` = '课程列表', `update_time` = NOW()
WHERE `id` = 41020 AND `deleted` = 0;
