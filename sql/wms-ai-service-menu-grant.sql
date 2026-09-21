-- 把新建的「AI 客服管理」菜单授权给相关角色，并加入其租户所用套餐
--
-- 背景：
--   1) 菜单 6750（AI 客服管理）及其 6 个子页面（6751/6754/6755/6756/6757/6758）已创建；
--   2) 角色 60084（企业超级管理员）已拥有全部 ai:* 权限，只缺这些菜单挂载点；
--   3) 套餐 119 决定了租户 2005 能看到哪些菜单，需要把新菜单追加进去。
--
-- 只做增量：不删除任何既有菜单、套餐项或角色授权，可重复执行。

-- ========== 1. 角色增量授权 ==========
-- 给「已经拥有 ai:model:query」（即能进 AI 模块管理台）的角色补上新菜单挂载点
-- 包含 6750 及其全部子节点（子页面 + AI 配置查询/保存两个按钮权限）
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT grants.role_id, target.id, '1', NOW(), '1', NOW(), b'0', grants.tenant_id
FROM system_role_menu grants
JOIN system_menu src ON src.id = grants.menu_id
JOIN system_menu target ON target.deleted = b'0'
    AND (target.id = 6750
         OR target.parent_id = 6750
         OR target.parent_id IN (SELECT id FROM (SELECT id FROM system_menu WHERE parent_id = 6750) x))
WHERE src.permission = 'ai:model:query' AND src.deleted = b'0' AND grants.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu e
                  WHERE e.role_id = grants.role_id AND e.menu_id = target.id
                    AND e.tenant_id = grants.tenant_id AND e.deleted = b'0');

-- ========== 2. 追加到租户2005 所用套餐的 menu_ids ==========
-- 新菜单只有 7 个，逐个 append 最稳妥，且天然幂等
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', 6750)
WHERE id = 119 AND JSON_CONTAINS(menu_ids, CAST(6750 AS JSON)) = 0;
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', 6751)
WHERE id = 119 AND JSON_CONTAINS(menu_ids, CAST(6751 AS JSON)) = 0;
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', 6754)
WHERE id = 119 AND JSON_CONTAINS(menu_ids, CAST(6754 AS JSON)) = 0;
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', 6755)
WHERE id = 119 AND JSON_CONTAINS(menu_ids, CAST(6755 AS JSON)) = 0;
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', 6756)
WHERE id = 119 AND JSON_CONTAINS(menu_ids, CAST(6756 AS JSON)) = 0;
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', 6757)
WHERE id = 119 AND JSON_CONTAINS(menu_ids, CAST(6757 AS JSON)) = 0;
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', 6758)
WHERE id = 119 AND JSON_CONTAINS(menu_ids, CAST(6758 AS JSON)) = 0;
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', 6752)
WHERE id = 119 AND JSON_CONTAINS(menu_ids, CAST(6752 AS JSON)) = 0;
UPDATE system_tenant_package SET menu_ids = JSON_ARRAY_APPEND(menu_ids, '$', 6753)
WHERE id = 119 AND JSON_CONTAINS(menu_ids, CAST(6753 AS JSON)) = 0;

-- ========== 3. 结果确认 ==========
SELECT '套餐119菜单数' AS item, CAST(JSON_LENGTH(menu_ids) AS CHAR) AS value
FROM system_tenant_package WHERE id = 119
UNION ALL
SELECT '角色60084新菜单数', CAST(COUNT(*) AS CHAR)
FROM system_role_menu rm JOIN system_menu m ON m.id = rm.menu_id
WHERE rm.role_id = 60084 AND rm.deleted = b'0'
  AND (m.id = 6750 OR m.parent_id = 6750 OR m.parent_id = 6751);
