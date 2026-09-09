-- 2026-09-10 租户授权收缩：平台功能不给租户用户
-- 背景：延河热力(tenant 2019) 角色 60346(甲方超级管理员)/60352(招标管理员) 曾获 role1(平台角色) 的 bidding 菜单集，
--       其中"外部公告审核管理 / 爬虫管理"属平台侧功能，按业务方要求撤销；经确认 6762 平台授权管理(bidding:subject-auth:*)
--       同属平台侧，一并撤销。保留：投标流程跟踪/公告公示管理/主体档案(企业自维护)/备案 等租户业务功能。
-- 执行账号：root@txgy-mysql（ruoyi-vue-pro 库），幂等可重跑。

-- 1) 撤销 6736 外部公告审核管理 + 6737 爬虫管理目录(含 6738-6740 页面与 6741-6745/6747 按钮)
DELETE FROM system_role_menu
WHERE role_id IN (60346, 60352)
  AND menu_id IN (6736, 6737, 6738, 6739, 6740, 6741, 6742, 6743, 6744, 6745, 6747);

-- 2) 撤销 6762 平台授权管理(页面 bidding:subject-auth:query) + 6763 主体授权/经办用户管理(按钮 bidding:subject-auth:update)
DELETE FROM system_role_menu
WHERE role_id IN (60346, 60352)
  AND menu_id IN (6762, 6763);

-- 3) 校验：60352 应仅剩租户业务菜单（6735 目录下：6748-6752 投标流程跟踪、6754-6758 公告管理、6759 目录、6760/6761 主体自维护、6764/6765 备案）
SELECT rm.menu_id, m.name
FROM system_role_menu rm
JOIN system_menu m ON m.id = rm.menu_id
WHERE rm.role_id = 60352
ORDER BY rm.menu_id;
