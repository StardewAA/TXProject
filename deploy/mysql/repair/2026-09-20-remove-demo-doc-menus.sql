-- =====================================================================
-- 【修复】移除侧边栏中与业务无关的框架演示菜单（外链）
--   1254 作者动态       https://www.iocoder.cn
--   2159 Boot 开发文档  https://doc.iocoder.cn/
--   2160 Cloud 开发文档 https://cloud.iocoder.cn
-- 说明：三者均为顶级菜单、无子菜单；system_tenant_package 未引用，
--       system_role_menu 亦无绑定（已核对），因此直接物理删除即可。
-- 用法：docker exec -i deploy-mysql-1 mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < 本文件
-- 幂等：可重复执行。
-- =====================================================================

DELETE FROM `system_role_menu` WHERE `menu_id` IN (1254, 2159, 2160);
DELETE FROM `system_menu` WHERE `id` IN (1254, 2159, 2160);