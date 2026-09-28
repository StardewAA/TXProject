-- 删除 CMS 内容管理下多余菜单：标准规范(70041) / 材料参数(70042) / 工艺案例(70043) / 定额资料(70044)
-- 由 Codex 更新 2026-09-22

SELECT '删除前（内容管理 70040 下的子菜单）' AS step;
SELECT id, name, path, sort, visible, deleted FROM system_menu WHERE parent_id = 70040 ORDER BY sort;

UPDATE system_role_menu
SET deleted = b'1', updater = '1', update_time = NOW()
WHERE menu_id IN (70041, 70042, 70043, 70044) AND deleted = b'0';

UPDATE system_menu
SET visible = b'0', deleted = b'1', updater = '1', update_time = NOW()
WHERE id IN (70041, 70042, 70043, 70044) AND deleted = b'0';

SELECT '删除后（内容管理 70040 下的子菜单）' AS step;
SELECT id, name, path, sort, visible, deleted FROM system_menu WHERE parent_id = 70040 ORDER BY sort;
