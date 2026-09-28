-- =====================================================================
-- 2026-09-21 报表配置中心：命名与层级调整
--
-- 背景：
--   「报表数据集」属于技术实现名称，对普通管理员不友好；
--   统一改为「报表配置中心」，并把它从 MES 系统顶层移到
--   「生产执行报表」之下，形成三段式权限结构：
--
--      生产执行报表
--        ├── 查询报表
--        ├── 导出报表
--        └── 报表配置中心
--              └── 新增报表
--
-- 原则（严格限定改动范围）：
--   1. 只改 `name`（6 条）与 `parent_id`（6800 一条）；
--   2. permission 一个字符都不改 ——
--        6800/6801 = report:dataset:query   （进入报表配置中心）
--        6802      = report:dataset:create  （新增报表，按钮 + 后端接口）
--        6803      = report:dataset:update
--        6804      = report:dataset:delete
--        6805      = report:dataset:execute
--   3. 不改 type / path / component / status / visible；
--   4. 不新增、不删除任何记录；
--   5. 不触碰 6900 生产执行报表、6901 查询报表、6902 导出报表
--      （report:center:query / report:center:export 完全保留原样）。
--
-- 说明：幂等，可重复执行。
-- =====================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- 1) 6800：从「MES 系统(5100)」移到「生产执行报表(6900)」下，并改名
UPDATE `system_menu`
SET `name`      = '报表配置中心',
    `parent_id` = 6900,
    `sort`      = 3,
    `updater`   = 'admin',
    `update_time` = NOW()
WHERE `id` = 6800;

-- 2) 6801~6805：仅改中文展示名，permission / type / parent_id 全部不变
UPDATE `system_menu`
SET `name` = '查看报表配置', `updater` = 'admin', `update_time` = NOW()
WHERE `id` = 6801;

UPDATE `system_menu`
SET `name` = '新增报表', `updater` = 'admin', `update_time` = NOW()
WHERE `id` = 6802;

UPDATE `system_menu`
SET `name` = '编辑报表', `updater` = 'admin', `update_time` = NOW()
WHERE `id` = 6803;

UPDATE `system_menu`
SET `name` = '删除报表', `updater` = 'admin', `update_time` = NOW()
WHERE `id` = 6804;

UPDATE `system_menu`
SET `name` = '预览报表', `updater` = 'admin', `update_time` = NOW()
WHERE `id` = 6805;

COMMIT;

-- 校验：
-- SELECT id, parent_id, name, permission, type FROM system_menu
--  WHERE id BETWEEN 6800 AND 6805 ORDER BY id;
-- 期望：
--   6800 | 6900 | 报表配置中心   | report:dataset:query  | 2
--   6801 | 6800 | 查看报表配置   | report:dataset:query  | 3
--   6802 | 6800 | 新增报表       | report:dataset:create | 3
--   6803 | 6800 | 编辑报表       | report:dataset:update | 3
--   6804 | 6800 | 删除报表       | report:dataset:delete | 3
--   6805 | 6800 | 预览报表       | report:dataset:execute| 3
