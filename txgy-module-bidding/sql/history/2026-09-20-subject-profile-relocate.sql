-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- 2026-09-20 主体档案归位：迁入「资质与证书管理」(60005000)
-- 背景：用户裁定 ① 主体档案搬到电子证照管理（资质与证书管理）下；② 招投标人共用同一主体档案（一租户一档，契约见
--   docs/电子证照前前端契约/后端变更请求-主体档案合并一租户一档-2026-09-20.md）。
-- 说明：
--   · 6760 主体档案（企业自维护）是 证照/资质/人员/安许 的持有主体前提，故排到资质与证书管理目录**第一位**（sort=0）。
--   · 6761（主体档案维护按钮）parent 跟随 6760 不变。
--   · 6759「主体信息与授权管理」目录保留：其下 6762/6763 平台授权管理（平台侧）仍留在原目录；若 6759 变空壳可后续再处理。
--   · 组件路径/权限码不变（bidding/subjectProfile/index + bidding:subject:query/update），仅父级与排序调整。
-- 执行账号：root@txgy-mysql（ruoyi-vue-pro 库），幂等可重跑。

-- 1) 主体档案页面 6760：parent 6759 → 60005000，sort 置 0（资质与证书管理第一位）
UPDATE system_menu
SET parent_id = 60005000, sort = 0
WHERE id = 6760 AND deleted = 0;

-- 2) 主体档案维护按钮 6761：保持挂在 6760 下（parent 不变，仅同步 sort 跟随页面）
UPDATE system_menu
SET sort = 1
WHERE id = 6761 AND deleted = 0;

-- 3) 校验：主体档案应挂在资质与证书管理下，且 6759 仅剩平台授权
SELECT id, name, parent_id, sort, component
FROM system_menu
WHERE id IN (6760, 6761, 6759, 6762, 6763, 60005000)
ORDER BY parent_id, sort;
