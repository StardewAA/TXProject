-- =====================================================================
-- 修复：危化品链菜单 id 撞了「环保检测报告」
--
-- 事故：chemical-20260910-menu.sql 把危化品的 6 个按钮排在 34397~34402，
--       其中 34400/34401/34402 早已被 shell-modules-menu-20260910.sql 用作
--       环保检测报告的 父菜单/查询/新增；该脚本的 DELETE ... BETWEEN 34396 AND 34402
--       因此删掉了环保报告的 34400~34402 与 60185/60466 的绑定，
--       并把 34403/34404（修改/删除）挂到了一个已是"按钮"的 34400 下面。
--       症状：huahan_anquan 在库位校验/五双签字上 403 —— 权限链取到的
--       menu_role_ids:2010:34401 还是旧值（环保专员 60466），而非 60468。
--
-- 修法：环保报告 34400~34402 原样恢复；危化品这 3 个按钮挪到空段 34460~34462
--       （34455~34499 经查未被占用）。34396~34399 无冲突，保持不动。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < chemical-20260910-menu-fix.sql
-- 之后必须重启 mes-server（内存权限缓存），见 docs/开发说明-2026-09-08.md §13.2。
-- =====================================================================

-- 0. 物理清掉撞位的行（菜单 id 是主键，逻辑删除挡不住重复插入）
DELETE FROM `system_menu`      WHERE `id` IN (34400, 34401, 34402);
DELETE FROM `system_role_menu` WHERE `menu_id` IN (34400, 34401, 34402);
DELETE FROM `system_menu`      WHERE `id` IN (34460, 34461, 34462);
DELETE FROM `system_role_menu` WHERE `menu_id` IN (34460, 34461, 34462);

-- 1. 把危化品 34400/34401/34402 三个按钮挪到 34460/34461/34462
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
  (34460, '删除',     'mes:set-chemical:delete', 3, 4, 34396, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34461, '库位校验', 'mes:set-chemical:check',  3, 5, 34396, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34462, '五双签字', 'mes:set-chemical:sign',   3, 6, 34396, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 2. 恢复「环保检测报告」34400~34402（定义取自 shell-modules-menu-20260910.sql:12-14）
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`) VALUES
  (34400, '环保检测报告', '', 2, 195, 34000, 'env-report', 'mes/safetyEnv/envReport/index', 'MesSetEnvReport', 0, b'1', b'1', b'1', '1', '1');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`) VALUES
  (34401, '查询', 'mes:set-env-report:query',  3, 1, 34400, '', 0, b'1', b'1', b'1', '1', '1'),
  (34402, '新增', 'mes:set-env-report:create', 3, 2, 34400, '', 0, b'1', b'1', b'1', '1', '1');

-- 3. 清掉撞位期间的错绑定（危化品曾在 34401/34402 上、且 60468 占了环保报告的名）
DELETE FROM `system_role_menu` WHERE `menu_id` IN (34400, 34401, 34402) AND `role_id` IN (60185, 60468);

-- 4. 恢复环保报告的原始绑定（60185 全绑；60466 环保专员 原绑 34400~34403，不含 34404）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `tenant_id`) VALUES
  (60185, 34400, 'system', 'system', 2010),
  (60185, 34401, 'system', 'system', 2010),
  (60185, 34402, 'system', 'system', 2010),
  (60466, 34400, 'system', 'system', 2010),
  (60466, 34401, 'system', 'system', 2010),
  (60466, 34402, 'system', 'system', 2010);

-- 5. 危化品的三个按钮按新 id 重新授权（管理员全绑；安全员 只 库位校验+五双签字）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `tenant_id`) VALUES
  (60185, 34460, 'system', 'system', 2010),
  (60185, 34461, 'system', 'system', 2010),
  (60185, 34462, 'system', 'system', 2010),
  (60468, 34461, 'system', 'system', 2010),
  (60468, 34462, 'system', 'system', 2010);

-- 6. 校验：环保报告 5 行齐、危化品 7 行齐、安全员绑 5 条且不含 34460
SELECT '环保报告34400-34404' k, COUNT(*) v FROM `system_menu` WHERE `id` BETWEEN 34400 AND 34404 AND `deleted` = 0
UNION ALL SELECT '危化品34396-34399+34460-34462', COUNT(*) FROM `system_menu` WHERE (`id` BETWEEN 34396 AND 34399 OR `id` BETWEEN 34460 AND 34462) AND `deleted` = 0
UNION ALL SELECT '安全员60468绑定数', COUNT(*) FROM `system_role_menu` WHERE `role_id` = 60468 AND `deleted` = 0
UNION ALL SELECT '安全员60468误绑删除键', COUNT(*) FROM `system_role_menu` WHERE `role_id` = 60468 AND `menu_id` = 34460 AND `deleted` = 0
UNION ALL SELECT '环保专员60466绑定数', COUNT(*) FROM `system_role_menu` WHERE `role_id` = 60466 AND `menu_id` BETWEEN 34400 AND 34404 AND `deleted` = 0;
