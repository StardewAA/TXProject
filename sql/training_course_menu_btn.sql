-- ======================================================================
-- 云培训学院 - 课程管理按钮权限（type=3）
-- 父菜单：课程管理（component = training/course/index，原菜单 id 41001）
-- 用途：与后端 Controller @PreAuthorize 权限点对齐
-- ======================================================================
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time)
SELECT * FROM (
  SELECT 41100 AS id, '课程查询' AS name, 'training:course:query' AS permission, 3 AS type, 1 AS sort, 41001 AS parent_id, NULL AS path, NULL AS icon, NULL AS component, NULL AS component_name, 0 AS status, b'1' AS visible, b'0' AS keep_alive, b'0' AS always_show, 'admin' AS creator, NOW() AS create_time, 'admin' AS updater, NOW() AS update_time
  UNION ALL SELECT 41101, '课程新增', 'training:course:create', 3, 2, 41001, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW()
  UNION ALL SELECT 41102, '课程修改', 'training:course:update', 3, 3, 41001, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW()
  UNION ALL SELECT 41103, '课程删除', 'training:course:delete', 3, 4, 41001, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW()
  UNION ALL SELECT 41104, '课程审核', 'training:course:audit', 3, 5, 41001, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW()
  UNION ALL SELECT 41105, '课程评论', 'training:course:comment', 3, 6, 41001, NULL, NULL, NULL, NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW()
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id IN (41100, 41101, 41102, 41103, 41104, 41105));
