-- ------------------------------------------------------------------------------
-- IM 即时通讯 - 「好友申请」菜单新增【删除】按钮权限（M2 好友申请页增强）
-- 权限标识：im:manager:friend-request:delete（与后端 ImFriendRequestManagerController#deleteFriendRequest 一致）
-- 兼容性：不同环境「好友申请」菜单 ID 可能不同，自动以 component = im/manager/friend/request/index 的菜单为父级
-- 幂等性：权限标识已存在时不重复插入，可重复执行
-- 附加：自动授权给各租户管理员角色（code = tenant_admin），避免逐租户手工勾选
-- ------------------------------------------------------------------------------

SET @request_parent_id = (
  SELECT id FROM `system_menu`
  WHERE `component` = 'im/manager/friend/request/index' AND `deleted` = b'0'
  LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
   `component`, `component_name`, `status`, `visible`, `keep_alive`,
   `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '好友申请删除', 'im:manager:friend-request:delete', 3, 2, @request_parent_id,
       '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE @request_parent_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'im:manager:friend-request:delete' AND `deleted` = b'0'
  );

INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.id, m.id, '1', NOW(), '1', NOW(), b'0', r.tenant_id
FROM `system_role` r
JOIN `system_menu` m ON m.`permission` = 'im:manager:friend-request:delete' AND m.`deleted` = b'0'
WHERE r.`code` = 'tenant_admin' AND r.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` rm
    WHERE rm.`role_id` = r.id AND rm.`menu_id` = m.id AND rm.`deleted` = b'0'
  );
