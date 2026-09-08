-- 2026-09-09 菜单归类修正：公告/公示管理（内部 notice）挂回招投标(6735) 内部 + 超管整棵子树授权
-- 背景：6753(公告/公示管理)+6754-6758 此前被误置于顶层 parent_id=0；招投标功能树 6735 在 role1/role2 均未授权，
--      导致平台超管/成员都看不到。本脚本：①归位 ②role1(super_admin) 全量授权 6735 子树(21 项)。幂等可重复执行。

-- ① 公告/公示管理 → 招投标(6735) 内部（目录 path=notice，前端组件 bidding/notice/index 不变）
UPDATE system_menu SET parent_id = 6735, path = 'notice' WHERE id = 6753 AND deleted = 0;

-- ② role1(super_admin) 授权 6735 整棵子树（含 6736 外部公告审核 / 6737 爬虫管理 / 6748 投标流程跟踪 / 6753 公告公示管理 及其按钮）
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
SELECT 1, id, 'admin', NOW(), 'admin', NOW(), 0 FROM system_menu
WHERE deleted = 0 AND id IN (6735,6736,6737,6738,6741,6742,6743,6745,6746,6747,
6748,6749,6750,6751,6752,6753,6754,6755,6756,6757,6758);

-- ③（可选）role2(common/成员) 只读公告/公示：若成员需要在招投标内浏览公告列表，放开下列节点（不含发布/删除）
-- INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
-- SELECT 2, id, 'admin', NOW(), 'admin', NOW(), 0 FROM system_menu WHERE deleted=0 AND id IN (6735,6753,6754,6755,6756);
-- 说明：6757(发布 bidding:notice:publish)/6758(删除) 不建议授予普通成员；外部公告审核(6736)/爬虫管理(6737 系)同理按职能取舍。

-- 附注：6759 主体信息与授权管理 目前仍在顶层(parent_id=0)，若同样属招投标内部，将 parent_id 改 6735 即可（未在脚本中自动执行，避免误并）；
-- 6735 下的 6748 投标流程跟踪/6749-6752 已由前端菜单先行创建，role1 已随 ② 一并授权。

