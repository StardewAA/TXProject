-- 安全环保检测(34000~34999) 字段级对齐：把 type/permission/component 等补齐
-- 起因：首轮对齐只同步了 name/parent_id/sort/component，漏了 type 与 permission，
--       导致 34450「附件查看」保留了本机原 type=2（应为按钮 type=3）+ 空组件，
--       菜单里出现一个点进去什么都没有的项。
-- 基准：_backup/20260921-tianxin/pre-tianxin-targeted.sql
-- 注意：visible/keep_alive/always_show 三个 bit 列在 dump 里是空值，
--       不能照抄，保持 b'1'（见 safetyEnv-align-20260923.sql ⑤）。
SET NAMES utf8mb4;
USE `ruoyi-vue-pro`;

UPDATE system_menu SET name='安全环保检测', permission='', type=1, sort=45, parent_id=5100, path='safetyEnv', icon='ep:warning', component='', component_name='', status=0, updater='sql', update_time=NOW() WHERE id=34000;
UPDATE system_menu SET name='附件查看', permission='mes:set-attachment:query', type=3, sort=10, parent_id=34370, path='', icon='', component='', component_name='', status=0, updater='sql', update_time=NOW() WHERE id=34450;
UPDATE system_menu SET name='附件维护', permission='mes:set-attachment:update', type=3, sort=11, parent_id=34370, path='', icon='', component='', component_name='', status=0, updater='sql', update_time=NOW() WHERE id=34451;

-- 校验：34450/34451 应为 type=3 的按钮，且不再作为菜单出现
SELECT id, name, type, permission, component FROM system_menu WHERE id IN (34450, 34451);
