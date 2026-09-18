-- ============================================================================
-- CRM 补齐缺失菜单：业绩目标设置 / 产品分析 / 业绩达成
-- 日期：2026-09-17
--
-- 背景：
--   这三个页面的前端视图与后端接口都在代码里（也存在于仓库基线
--   sql/00-sql文件/sql/mysql/ruoyi-vue-pro.sql 的 system_menu 定义中），
--   但本库的 system_menu 缺这三条记录，导致左侧导航看不见：
--     1) CRM 系统 → 系统配置 → 业绩目标设置   crm/performance/config/index
--     2) CRM 系统 → 数据统计 → 产品分析       crm/statistics/product/index
--     3) CRM 系统 → 数据统计 → 业绩达成       crm/statistics/performanceTarget/index
--   对应后端接口：
--     GET /admin-api/crm/performance-config/*        （CrmPerformanceConfigController）
--     GET /admin-api/crm/statistics-product/*        （CrmStatisticsProductController）
--     GET /admin-api/crm/statistics-performance-target/*（CrmStatisticsPerformanceTargetController）
--
-- 处理原则：
--   1. 幂等：按 id 判断，已存在则跳过；
--   2. 菜单号取 2925~2933（CRM 号段内的空闲区间；刻意避开 32600~32699，
--      因为 2026-09-08-crm-after-sale-menu.sql 会按该区间整段 DELETE）；
--   3. 只影响租户 2012 的角色授权与租户套餐。
-- ============================================================================

USE `ruoyi-vue-pro`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 菜单：三个功能页（type=2）
-- ---------------------------------------------------------------------------
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 2925, '产品分析', 'crm:statistics-product:query', 2, 6, 2560, 'product', 'ep:goods',
       'crm/statistics/product/index', 'CrmStatisticsProduct', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 2925);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 2927, '业绩达成', 'crm:statistics-performance-target:query', 2, 7, 2560, 'performance-target', 'ep:aim',
       'crm/statistics/performanceTarget/index', 'CrmStatisticsPerformanceTarget', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 2927);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 2929, '业绩目标设置', 'crm:performance-config:query', 2, 6, 2524, 'performance-config', 'ep:setting',
       'crm/performance/config/index', 'CrmPerformanceConfig', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 2929);

-- ---------------------------------------------------------------------------
-- 2. 按钮级权限（type=3），与仓库基线 SQL 保持一致
-- ---------------------------------------------------------------------------
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 2926, '产品分析查询', 'crm:statistics-product:query', 3, 1, 2925, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 2926);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 2928, '业绩达成查询', 'crm:statistics-performance-target:query', 3, 1, 2927, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 2928);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 2930, '业绩目标设置查询', 'crm:performance-config:query', 3, 1, 2929, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 2930);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 2931, '业绩目标设置创建', 'crm:performance-config:create', 3, 2, 2929, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 2931);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 2932, '业绩目标设置更新', 'crm:performance-config:update', 3, 3, 2929, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 2932);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                         status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 2933, '业绩目标设置删除', 'crm:performance-config:delete', 3, 4, 2929, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 2933);

-- ---------------------------------------------------------------------------
-- 3. 角色授权：与 CRM 根菜单（2397）的授权范围保持一致
-- ---------------------------------------------------------------------------
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, tenant_id, deleted)
SELECT rm.role_id, m.id, '1', NOW(), '1', NOW(), rm.tenant_id, b'0'
FROM system_role_menu rm
         JOIN system_menu m ON m.id IN (2925, 2926, 2927, 2928, 2929, 2930, 2931, 2932, 2933)
WHERE rm.menu_id = 2397
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.id);

-- ---------------------------------------------------------------------------
-- 4. 租户套餐：把新菜单追加进 menu_ids（含 CRM 根菜单的套餐）
-- ---------------------------------------------------------------------------
UPDATE system_tenant_package
SET menu_ids = CONCAT(SUBSTRING(menu_ids, 1, CHAR_LENGTH(menu_ids) - 1), ',2925,2926,2927,2928,2929,2930,2931,2932,2933]')
WHERE deleted = b'0'
  AND menu_ids LIKE '%]'
  AND menu_ids NOT LIKE '%2929%'
  AND menu_ids LIKE '%2397%';

-- ---------------------------------------------------------------------------
-- 5. 校验
-- ---------------------------------------------------------------------------
SELECT m.id, m.name, p.name AS 上级, m.component, m.permission
FROM system_menu m
         LEFT JOIN system_menu p ON p.id = m.parent_id
WHERE m.id BETWEEN 2925 AND 2933
  AND m.deleted = b'0'
ORDER BY m.id;

SELECT COUNT(*) AS 角色授权条数 FROM system_role_menu WHERE menu_id BETWEEN 2925 AND 2933;
