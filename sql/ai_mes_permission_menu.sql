-- AI 角色权限配置：菜单按钮权限 + 企业超管自动授权
-- 1. 在「AI 大模型 - 聊天角色」(id=2773) 下新增两个按钮权限
INSERT INTO system_menu (name, permission, type, sort, parent_id, status, creator, updater)
SELECT '聊天角色权限配置查询', 'ai:chat-role-permission:query', 3, 5, 2773, 0, '1', '1'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'ai:chat-role-permission:query' AND deleted = 0);

INSERT INTO system_menu (name, permission, type, sort, parent_id, status, creator, updater)
SELECT '聊天角色权限配置保存', 'ai:chat-role-permission:update', 3, 6, 2773, 0, '1', '1'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'ai:chat-role-permission:update' AND deleted = 0);

-- 2. 给所有租户的企业超级管理员角色自动授权（免去逐个租户手动分配）
INSERT INTO system_role_menu (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, m.id, '1', '1', b'0', r.tenant_id
FROM system_role r
JOIN system_menu m ON m.permission IN ('ai:chat-role-permission:query', 'ai:chat-role-permission:update') AND m.deleted = 0
WHERE r.code = 'tenant_admin' AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0
  );
