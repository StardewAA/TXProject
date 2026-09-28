-- ------------------------------------------------------------------------------
-- IM 即时通讯 - 「好友列表」菜单新增【解除关系】按钮权限
-- 权限标识：im:manager:friend:delete（与后端 ImFriendManagerController#deleteFriend 一致）
-- 兼容性：不同环境「好友列表」菜单 ID 可能不同，自动以 component = im/manager/friend/index 的菜单为父级
-- 幂等性：权限标识已存在时不重复插入，可重复执行
-- ------------------------------------------------------------------------------

SET @friend_parent_id = (
  SELECT id FROM `system_menu`
  WHERE `component` = 'im/manager/friend/index' AND `deleted` = b'0'
  LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
   `component`, `component_name`, `status`, `visible`, `keep_alive`,
   `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '好友关系解除', 'im:manager:friend:delete', 3, 1, @friend_parent_id,
       '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE @friend_parent_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'im:manager:friend:delete' AND `deleted` = b'0'
  );
