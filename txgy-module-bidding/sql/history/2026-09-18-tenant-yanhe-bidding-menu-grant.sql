-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- ============================================================================
-- 2026-09-18 租户「延河热力」(tenant 2019) 开通招投标(bidding)业务菜单
-- 目标：让超级管理员「西安鸥鹏」(role 1, super_admin, 旁路权限) 里的招投标模块功能
--       在租户「延河」里也生效 —— 给租户业务角色授招投标(6735)下的业务子菜单。
-- 背景：库内菜单号段经历过与 MES「项目管理」(6750~6768) 的碰撞重排，
--       招投标业务菜单终态号段为 6748/6770/6783/6789/6800 各子树（见下方枚举）；
--       历史授权脚本里写的 6750~6765 号段已指向 MES 菜单，不能再用。
-- 口径：仅授「租户业务功能」，平台侧功能（外部公告审核 6736 / 信息聚合任务 6737
--       及爬虫 6738,6741~6743,6747 / 平台授权管理 6792,6793）不授，与
--       2026-09-10-revoke-platform-menus-tenant.sql 的收缩口径一致。
-- 执行账号：root（ruoyi-vue-pro 库），幂等可重跑（NOT EXISTS 守卫 + 无唯一键冲突）。
-- ============================================================================

INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT r.id, m.id, 2019, '1', NOW(), '1', NOW(), b'0'
FROM system_role r
JOIN system_menu m ON m.id IN (
    -- 顶层目录（6735 招投标 已随角色在侧边栏展开，此处保证两角色均持有）
    6735,
    -- 投标流程跟踪（6748 页面 + 6749/6780/6781/6782 按钮 + 6794/6795 备案）
    6748, 6749, 6780, 6781, 6782, 6794, 6795,
    -- 项目与标段（6770 目录 + 6771/6772 页面 + 6773~6776 按钮）
    6770, 6771, 6772, 6773, 6774, 6775, 6776,
    -- 公告/公示管理（6783 目录 + 6784 页面 + 6785~6788 按钮）
    6783, 6784, 6785, 6786, 6787, 6788,
    -- 主体信息与授权管理（6789 目录 + 6790 主体档案自维护 + 6791 维护按钮；不含 6792/6793 平台授权）
    6789, 6790, 6791,
    -- 标书智能体（6800 目录 + 6801/6802 页面 + 6803~6809 按钮）
    6800, 6801, 6802, 6803, 6804, 6805, 6806, 6807, 6808, 6809
)
WHERE r.id IN (60346, 60352)          -- 延河热力：60346 甲方超级管理员 / 60352 招标管理员
  AND r.deleted = 0
  AND m.deleted = 0
  AND NOT EXISTS (
        SELECT 1 FROM system_role_menu x
        WHERE x.role_id = r.id AND x.menu_id = m.id AND x.deleted = 0
  );

-- ============================================================================
-- 校验：两个角色应各持有 34 个招投标业务菜单（含顶层 6735），且不含平台侧 6736/6737/6792/6793
-- ============================================================================
SELECT rm.role_id, r.name AS role_name, COUNT(*) AS bidding_menus
FROM system_role_menu rm
JOIN system_role r ON r.id = rm.role_id
JOIN system_menu m ON m.id = rm.menu_id
WHERE rm.role_id IN (60346, 60352)
  AND rm.deleted = 0 AND m.deleted = 0
  AND (m.permission LIKE 'bidding:%' OR m.name = '招投标')
GROUP BY rm.role_id, r.name;

-- 期望：无平台侧菜单泄入（结果应为空）
SELECT rm.role_id, m.id, m.name
FROM system_role_menu rm
JOIN system_menu m ON m.id = rm.menu_id
WHERE rm.role_id IN (60346, 60352)
  AND rm.deleted = 0
  AND m.id IN (6736, 6737, 6738, 6741, 6742, 6743, 6747, 6792, 6793);
