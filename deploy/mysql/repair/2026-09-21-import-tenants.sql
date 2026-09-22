-- 2026-09-21 导入导出文件中的「租户 + 租户关联数据」（只增不改，可重复执行）
--
-- 背景：模块数据上一轮已导入，但其中 2005/2007/2009/2010/2011/2012 等租户在本地 system_tenant 里不存在，
--       数据进了库却看不到。本脚本把厂商整库导出里的租户及其关联数据全部补齐。
-- 来源：sql/202609201700/MES_202609201615.sql 的 system_* 表
-- 预处理：
--   node .docker-local/import/build.js --only=system_ sql/202609201700/MES_202609201615.sql .docker-local/import/out_system.sql
--   docker exec -i deploy-mysql-1 mysql -uroot -proot txgy_stage < .docker-local/import/out_system.sql
--
-- 原则：只新增、不覆盖。全部语句都带判重，重复执行无副作用；
--       已存在的租户（1/121/122/3000）与其数据完全不动。

SET NAMES utf8mb4;

-- 1) 租户：导出 33 个 -> 本地 4 个，补齐 30 个
INSERT INTO `ruoyi-vue-pro`.system_tenant
SELECT s.* FROM txgy_stage.system_tenant s
LEFT JOIN `ruoyi-vue-pro`.system_tenant l ON l.id = s.id
WHERE l.id IS NULL;

-- 2) 租户套餐：补 114/115/116/117/118/119/120（新租户的 package_id 指向它们）
--    先对齐列类型：导出文件（厂商生产）里 system_tenant_package.menu_ids 是 text，
--    本地基础脚本是 varchar(4096)，而 116/117/119/120 的 menu_ids 有 4.9K~8.6K，直接导入会报 1406。
ALTER TABLE `ruoyi-vue-pro`.system_tenant_package
  MODIFY COLUMN `menu_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联的菜单编号';
INSERT INTO `ruoyi-vue-pro`.system_tenant_package
SELECT s.* FROM txgy_stage.system_tenant_package s
LEFT JOIN `ruoyi-vue-pro`.system_tenant_package l ON l.id = s.id
WHERE l.id IS NULL;

-- 3) 新租户菜单授权要用到、而本地缺失的菜单（安全环保 34000/343xx、MES 工装 345xx、AI 34552/34553，共 41 条）
INSERT INTO `ruoyi-vue-pro`.system_menu
SELECT s.* FROM txgy_stage.system_menu s
LEFT JOIN `ruoyi-vue-pro`.system_menu l ON l.id = s.id
WHERE l.id IS NULL;

-- 4) 字典类型 / 字典项（全局表，按主键补缺）
INSERT INTO `ruoyi-vue-pro`.system_dict_type
SELECT s.* FROM txgy_stage.system_dict_type s
LEFT JOIN `ruoyi-vue-pro`.system_dict_type l ON l.id = s.id
WHERE l.id IS NULL;

INSERT INTO `ruoyi-vue-pro`.system_dict_data
SELECT s.* FROM txgy_stage.system_dict_data s
LEFT JOIN `ruoyi-vue-pro`.system_dict_data l ON l.id = s.id
WHERE l.id IS NULL;

-- 5) 新租户的部门 / 岗位
INSERT INTO `ruoyi-vue-pro`.system_dept
SELECT s.* FROM txgy_stage.system_dept s
LEFT JOIN `ruoyi-vue-pro`.system_dept l ON l.id = s.id
WHERE s.tenant_id IN (162,163,164,165,166,167,2001,2002,2003,2004,2005,2006,2007,2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023,2024) AND l.id IS NULL;

INSERT INTO `ruoyi-vue-pro`.system_post
SELECT s.* FROM txgy_stage.system_post s
LEFT JOIN `ruoyi-vue-pro`.system_post l ON l.id = s.id
WHERE s.tenant_id IN (162,163,164,165,166,167,2001,2002,2003,2004,2005,2006,2007,2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023,2024) AND l.id IS NULL;

-- 6) 新租户的角色（导出里的角色 id=162 与本地租户 1 的「HRM团队负责人」撞号，见第 7 步）
INSERT INTO `ruoyi-vue-pro`.system_role
SELECT s.* FROM txgy_stage.system_role s
LEFT JOIN `ruoyi-vue-pro`.system_role l ON l.id = s.id
WHERE s.tenant_id IN (162,163,164,165,166,167,2001,2002,2003,2004,2005,2006,2007,2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023,2024) AND l.id IS NULL;

-- 7) 撞号角色 162（导出里属租户 164「租户管理员」）改挂新 id 900162，避免覆盖租户 1 的 HRM团队负责人
INSERT INTO `ruoyi-vue-pro`.system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 900162, s.name, s.code, s.sort, s.data_scope, s.data_scope_dept_ids, s.status, s.type, s.remark, s.creator, s.create_time, s.updater, s.update_time, s.deleted, s.tenant_id
FROM txgy_stage.system_role s
LEFT JOIN `ruoyi-vue-pro`.system_role l ON l.id = 900162
WHERE s.id = 162 AND l.id IS NULL;

-- 8) 新租户的用户
INSERT INTO `ruoyi-vue-pro`.system_users
SELECT s.* FROM txgy_stage.system_users s
LEFT JOIN `ruoyi-vue-pro`.system_users l ON l.id = s.id
WHERE s.tenant_id IN (162,163,164,165,166,167,2001,2002,2003,2004,2005,2006,2007,2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023,2024) AND l.id IS NULL;

-- 9) 授权关系：用户-角色 / 用户-岗位 / 角色-菜单（id 交由自增，按业务键判重）
INSERT INTO `ruoyi-vue-pro`.system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT s.user_id, IF(s.role_id = 162, 900162, s.role_id), s.creator, s.create_time, s.updater, s.update_time, s.deleted, s.tenant_id
FROM txgy_stage.system_user_role s
LEFT JOIN `ruoyi-vue-pro`.system_user_role l ON l.user_id = s.user_id AND l.role_id = IF(s.role_id = 162, 900162, s.role_id)
WHERE s.tenant_id IN (162,163,164,165,166,167,2001,2002,2003,2004,2005,2006,2007,2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023,2024) AND l.id IS NULL;

INSERT INTO `ruoyi-vue-pro`.system_user_post (user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT s.user_id, s.post_id, s.creator, s.create_time, s.updater, s.update_time, s.deleted, s.tenant_id
FROM txgy_stage.system_user_post s
LEFT JOIN `ruoyi-vue-pro`.system_user_post l ON l.user_id = s.user_id AND l.post_id = s.post_id
WHERE s.tenant_id IN (162,163,164,165,166,167,2001,2002,2003,2004,2005,2006,2007,2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023,2024) AND l.id IS NULL;

INSERT INTO `ruoyi-vue-pro`.system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT IF(s.role_id = 162, 900162, s.role_id), s.menu_id, s.creator, s.create_time, s.updater, s.update_time, s.deleted, s.tenant_id
FROM txgy_stage.system_role_menu s
JOIN txgy_stage.system_role r ON r.id = s.role_id AND r.tenant_id IN (162,163,164,165,166,167,2001,2002,2003,2004,2005,2006,2007,2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023,2024)
LEFT JOIN `ruoyi-vue-pro`.system_role_menu l ON l.role_id = IF(s.role_id = 162, 900162, s.role_id) AND l.menu_id = s.menu_id
WHERE l.id IS NULL;

-- 校验（期望：30 / 7 / 41 / 87 / 357 / 171 / 468 / 479 / 941 / 939 / 934 / 37455 量级）
SELECT (SELECT COUNT(*) FROM `ruoyi-vue-pro`.system_tenant) AS tenants,
       (SELECT COUNT(*) FROM `ruoyi-vue-pro`.system_tenant_package) AS packages,
       (SELECT COUNT(*) FROM `ruoyi-vue-pro`.system_users) AS users_,
       (SELECT COUNT(*) FROM `ruoyi-vue-pro`.system_role) AS roles;
