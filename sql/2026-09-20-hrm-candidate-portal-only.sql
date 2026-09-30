-- ============================================================
-- HRM 候选人来源收敛：候选人只能由求职者在招聘门户投递产生
--
-- 背景：后台「候选人 → 新增」允许 HR 手工建候选人，但这类候选人没有应聘记录
--       （hrm_recruit_application 没有对应行），导致：
--         · 列表出现「发 Offer」，点击必报「应聘编号不能为空」
--         · 无法安排面试、无法走 Offer/转员工，形成脏数据
--       经确认业务口径：候选人只能从门户投递进入，后台不提供新增入口。
--
-- 本脚本：
--   1. 停用「候选人新增」按钮权限（hrm:recruit:candidate:create），
--      使其不再出现在角色授权树里，避免被重新勾选；
--   2. 回收已授权关系（防止存量角色继续持有该权限）。
-- 说明：后端 POST /hrm/recruit/candidate/create 已加硬校验，即使被直接调用也会被拒绝。
-- 幂等：可重复执行。
-- ============================================================

-- 1. 停用「候选人新增」按钮权限
UPDATE `system_menu`
SET `status`     = 1,
    `deleted`    = b'1',
    `updater`    = 'candidate-portal-only-dev',
    `update_time` = NOW()
WHERE `permission` = 'hrm:recruit:candidate:create'
  AND `deleted` = b'0';

-- 2. 回收该权限的角色授权（若角色还持有，会出现「有权限但页面没有入口」的困惑）
DELETE FROM `system_role_menu`
WHERE `menu_id` IN (SELECT `id` FROM `system_menu` WHERE `permission` = 'hrm:recruit:candidate:create');

-- 3. 核对：应返回 0 行
-- SELECT id, name, permission, status, deleted FROM system_menu WHERE permission = 'hrm:recruit:candidate:create' AND deleted = b'0';
