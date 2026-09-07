-- =====================================================================
-- 环保污染监控 - 清理「安全环保检测」目录下与污染判定无关的历史功能菜单
-- 背景：34001~34018+overview 属 20260902 交付的「安全环保检测(SET)」宽模块
--       （气体/消防/危化品/危废/检测标准/计划/噪声/粉尘/电气/压力容器/PPE/
--         职业病/排放口/废气/废水/碳排放/排污许可/环保报告/概览），与本次
--       「环保污染监控-污染判定」非同一逻辑，按用户要求从菜单移除，仅留污染判定。
-- 说明：仅删 system_menu/system_role_menu 行（页面/后端/表均保留在代码库，
--       mes_set_* 表数据不删）；如需恢复执行 set-module-20260902-menu.sql 即可。
-- 保留：id=34000 目录「安全环保检测」；id=34301 污染判定 + 34311~34315 按钮。
-- 执行：mysql -uroot -proot ruoyi-vue-pro < set-pollution-check-20260903-cleanup-menu.sql
--       应用后清 Redis（menu_role_ids:* / permission_menu_ids:* / role:*）并重新登录。
-- =====================================================================

DELETE FROM system_menu WHERE id BETWEEN 34001 AND 34299;
DELETE FROM system_role_menu WHERE menu_id BETWEEN 34001 AND 34299;

-- 核对：应只剩 34000 目录 + 34301 功能 + 34311~34315 按钮
SELECT id, name, type, parent_id FROM system_menu WHERE id BETWEEN 34000 AND 34400 AND deleted = 0 ORDER BY id;
