-- =====================================================================
-- 2026-09-20 权限体系根治：权限码承载补齐 + 租户字段修正 + 套餐同步
--
-- 【背景：本轮体检结论】
--   ① 「接口要求但无菜单承载」的权限码 37 个
--      → 菜单是权限码的唯一承载体（PermissionServiceImpl.hasAnyPermission 先按
--        permission_menu_ids:{码} 找菜单，再按 menu_role_ids:{租户}:{菜单} 找角色），
--        无承载 → 该权限码永远无法授予任何角色 → 接口永久 403。
--        super_admin 绕过权限校验，故该缺陷在超管视角不可见。
--   ② system_role_menu.tenant_id = 0 的记录 1838 条（角色却属各租户）
--      → 缓存按 menu_role_ids:{租户}:{菜单} 查询时命中不到 → 已授权限全部失效。
--   ③ 菜单 6748「投标流程跟踪」页面权限码错配（写成 notice-external:query，
--        与 component=bidding/processTrack/index 不符）→ process-track:query 无承载。
--
-- 【口径】不改任何接口权限码（用户裁定：接口权限不能改，改为补齐承载与数据）。
--
-- 【幂等】全部语句可重复执行（INSERT 均带判重）。
--
-- 【执行后必做】清 Redis 权限缓存，否则缓存不刷新、修复不生效：
--   redis-cli --scan --pattern 'menu_role_ids:*'        | xargs -r redis-cli DEL
--   redis-cli --scan --pattern 'permission_menu_ids:*'  | xargs -r redis-cli DEL
--
-- 【执行库】ruoyi-vue-pro
-- =====================================================================
SET NAMES utf8mb4;
SET SESSION group_concat_max_len = 100000;

-- ---------------------------------------------------------------------
-- ① 修正 system_role_menu.tenant_id：与所属角色的租户保持一致
-- ---------------------------------------------------------------------
UPDATE system_role_menu rm
JOIN system_role r ON r.id = rm.role_id
SET rm.tenant_id = r.tenant_id
WHERE rm.tenant_id = 0 AND r.tenant_id > 0 AND rm.deleted = b'0';

-- ---------------------------------------------------------------------
-- ② 修正 6748「投标流程跟踪」页面菜单的权限码（原错配为 notice-external:query）
-- ---------------------------------------------------------------------
UPDATE system_menu SET permission = 'bidding:process-track:query'
WHERE id = 6748 AND permission = 'bidding:notice-external:query';

-- ---------------------------------------------------------------------
-- ③ 为 37 个「接口要求但无菜单承载」的权限码补建承载菜单
--    形态：type=3 按钮、visible=0（不出现在导航，但可在角色授权界面勾选）
--    挂载：招投标目录 6735 下
-- ---------------------------------------------------------------------
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, updater, deleted)
SELECT t.* FROM (
  SELECT '公告查询' AS name, 'bidding:notice:query' AS permission, 3 AS type, 100 AS sort, 6735 AS parent_id, '' AS path, '' AS icon, NULL AS component, NULL AS component_name, 0 AS status, b'0' AS visible, b'1' AS keep_alive, b'1' AS always_show, '1' AS creator, '1' AS updater, b'0' AS deleted
  UNION ALL SELECT '公告起草', 'bidding:notice:create', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '公告编辑', 'bidding:notice:update', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '公告删除', 'bidding:notice:delete', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '公告发布', 'bidding:notice:publish', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '标书审查查询', 'bidding:bid-review:query', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '标书审查建单', 'bidding:bid-review:create', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '标书审查更新', 'bidding:bid-review:update', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '标书审查复核', 'bidding:bid-review:audit', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '审查规则查询', 'bidding:bid-review:rule:query', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '审查规则维护', 'bidding:bid-review:rule:update', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '审查配置维护', 'bidding:bid-review:config:update', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '资格预审查询', 'bidding:prequal:query', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '资格预审保存', 'bidding:prequal:save', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '资格预审发布', 'bidding:prequal:publish', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '资格预审申请', 'bidding:prequal:apply', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '资格预审评审', 'bidding:prequal:review', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '资格预审关闭', 'bidding:prequal:close', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '开标查询', 'bidding:opening:query', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '开标创建', 'bidding:opening:create', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '开标进房', 'bidding:opening:enter', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '开标管理', 'bidding:opening:manage', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '合同查询', 'bidding:contract:query', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '合同登记', 'bidding:contract:save', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '合同归档', 'bidding:contract:archive', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '保证金查询', 'bidding:deposit:query', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '保证金登记', 'bidding:deposit:save', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '保证金退还', 'bidding:deposit:refund', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '异议查询', 'bidding:objection:query', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '异议提出', 'bidding:objection:propose', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '异议答复', 'bidding:objection:reply', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '评标报告查询', 'bidding:report:query', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '评标报告提交', 'bidding:report:submit', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT 'CA签章台账查询', 'bidding:ca:query', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT 'CA签章验签', 'bidding:ca:verify', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '取消投标', 'bidding:process-track:cancel', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
  UNION ALL SELECT '采集任务更新', 'bidding:crawl-task:update', 3, 100, 6735, '', '', NULL, NULL, 0, b'0', b'1', b'1', '1', '1', b'0'
) AS t
WHERE t.permission NOT IN (SELECT permission FROM system_menu WHERE deleted = b'0');

-- ---------------------------------------------------------------------
-- ④ 各租户「租户管理员」授予全部 bidding 菜单（tenant_id 取角色真实租户）
-- ---------------------------------------------------------------------
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, '1', NOW(), '1', NOW(), b'0', r.tenant_id
FROM system_role r CROSS JOIN system_menu m
WHERE r.code = 'tenant_admin' AND m.permission LIKE 'bidding%' AND m.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                  WHERE x.role_id = r.id AND x.menu_id = m.id AND x.deleted = b'0');

-- ---------------------------------------------------------------------
-- ⑤ 在用租户套餐同步：追加全部 bidding 菜单 id
--    （否则租户管理员在「角色分配菜单」界面看不到这些菜单——该界面的
--      GET /system/menu/list-all-simple 按套餐过滤 menu_ids）
-- ---------------------------------------------------------------------
UPDATE system_tenant_package p
SET menu_ids = CONCAT(LEFT(p.menu_ids, CHAR_LENGTH(p.menu_ids) - 1), ',',
  (SELECT GROUP_CONCAT(id ORDER BY id) FROM system_menu WHERE permission LIKE 'bidding%' AND deleted = b'0'), ']')
WHERE p.id IN (119, 120) AND p.menu_ids LIKE '%]';

-- ---------------------------------------------------------------------
-- 校验段：执行后应满足 —— 无「接口要求但无菜单承载」的权限码
-- ---------------------------------------------------------------------
SELECT (SELECT COUNT(DISTINCT permission) FROM system_menu WHERE permission LIKE 'bidding%' AND deleted = b'0') AS menu_perm_cnt,
       (SELECT COUNT(*) FROM system_role_menu rm JOIN system_menu m ON m.id = rm.menu_id
        WHERE m.permission LIKE 'bidding%' AND rm.deleted = b'0') AS grant_rows;
