-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-29 权限审计批次 —— 菜单登记 + 菜单项归位/去重 + 授权（统一脚本）
--
-- 合并来源（原文件已删除，仅保留本终态）：
--   · 2026-09-29-bidding-missing-perm-menus.sql（4 个缺失权限点登记）
--   · 2026-09-29-菜单祖先授权补齐.sql（菜单祖先授权断链修复）
--
-- 【打包约定】本文件按 sql/tools/build-credential-sync.js 的 MENU_EXTRA_SOURCES 口径编写：
--   ① 授权段（节标题为「角色授权」的那一行）之前 = 菜单/权限点注册 → 并入一键包 master-credential-sync.sql；
--   ② 该行之后 = 授权语句，属部署态业务决策，**不进一键包**；
--   ③ 校验段（节标题为「校验」的那一行）处打包器截断。
--   ⚠️ 教训：打包器用 indexOf 找那两个节标题标记，**注释里也不能写标记原文** ——
--      本文件首版把标记原文写进了本段说明，结果正文被"在注释里"提前截断、整段菜单注册被静默丢弃
--      （2026-09-29 实测：镜像门禁报「缺 4 项」）。故本段一律用「」描述，不复制标记。
--   改动本文件后须重跑：node sql/tools/build-credential-sync.js（重建）→ check-master-mirror / check-permission-coverage。
--
-- 【与三台账重构的关系】2026-09-28-credential-three-ledger-ia.sql 把 主体档案 从 6760 迁到
--   设置目录(60006000)下的新页 60006100。故本脚本：登记时用**重构前的既有父页 6760**（保证在任何库都能挂上），
--   再用条件 UPDATE 在「重构已落地（60006100 存在）」时把主体认证两行归位到 60006100 —— 两种库都正确，可重复执行。
--
-- 【号段归属（先登记再占用）】60006300~60006303 = 本批次；60006000~60006101 / 60006200 /
--   60006400~60006501 / 60006601~60006603 = 三台账重构批次；600062xx 其余 = 流程子域批次。
--   禁止再向 600063xx 登记其他语义（同段不同义会导致菜单名与权限静默错位）。
-- ============================================================================

-- ========== ① 4 个缺失权限点登记（审计：代码 @PreAuthorize 有、system_menu 无承载 → 非超管恒 403） ==========
-- 主体认证 SubjectAuthController → 主体档案页 6760 下；招标文件下载 TenderDocDownloadController → 项目台账 6771 下。
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(60006300, '主体认证查询', 'bidding:subject-auth:query', 3, 100, 6760, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006301, '主体认证审核', 'bidding:subject-auth:update', 3, 101, 6760, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006302, '招标文件下载名单', 'bidding:tender-doc:query', 3, 102, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(60006303, '记录招标文件下载', 'bidding:tender-doc:download', 3, 103, 6771, NULL, NULL, NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ========== ①b 菜单项归位：三台账重构落地后，主体认证两行迁到新主体档案页 60006100 ==========
-- 条件 UPDATE：重构未执行时不动（当前库即此状态），可重复执行。
UPDATE `system_menu` SET `parent_id` = 60006100, `updater` = '1', `update_time` = NOW()
 WHERE `id` IN (60006300, 60006301) AND `deleted` = b'0'
   AND EXISTS (SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `id` = 60006100 AND `deleted` = b'0') t);

-- ========== ①c 菜单项去重：三台账重构落地后，旧页 6760/6761 让位给 60006100/60006101 ==========
-- 问题：重构把 主体档案 新建成 60006100（permission 与旧页 6760 **同码** bidding:subject:query），
--   旧页仍在 → 侧边栏出现两个「主体档案」、角色授权界面出现两个同码权限点。
-- 口径（沿用仓库先例，勿改成 DELETE）：权限点靠 system_menu 行承载，直接 DELETE 会连带回收
--   system_role_menu 授权 → 已授权角色**静默失权**（接口 403、菜单消失但不报错）；
--   故把旧页**降级为按钮**（type=3、清空 path/component，id 不变 → 授权行继续有效），侧边栏不再重复。
UPDATE `system_menu`
   SET `type` = 3, `sort` = 99, `path` = '', `component` = '', `component_name` = '',
       `name` = '主体档案（旧入口·已并入设置）', `updater` = '1', `update_time` = NOW()
 WHERE `id` = 6760 AND `deleted` = b'0'
   AND EXISTS (SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `id` = 60006100 AND `deleted` = b'0') t);

UPDATE `system_menu` SET `parent_id` = 60006100, `updater` = '1', `update_time` = NOW()
 WHERE `id` = 6761 AND `deleted` = b'0'
   AND EXISTS (SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `id` = 60006100 AND `deleted` = b'0') t);

-- ② 角色授权（打包器在本行截断；本段及以下**不进一键包**，属部署态业务决策）
-- 授权属部署态业务决策，不进一键包（打包器在此截断）：
--   · 4 个新权限点：沿用所属页面既有授权（能看到页面 → 拿到按钮）；
--   · ③-1/③-2：修菜单祖先授权断链（只补**无权限码的目录** 6735 → 零权限扩大，仅恢复菜单可见性）。
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` IN (60006300, 60006301)
JOIN `system_menu` p ON p.`id` = m.`parent_id`
WHERE rm.`menu_id` = p.`id` AND rm.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`id` AND x.`deleted` = b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`id`, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` IN (60006302, 60006303)
WHERE rm.`menu_id` = 6771 AND rm.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`id` AND x.`deleted` = b'0');

-- ③-1 持有「挂在 6735 下的页面」的角色 → 补根目录 6735
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, 6735, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` = rm.`menu_id` AND m.`deleted` = b'0' AND m.`id` <> 6735 AND m.`parent_id` = 6735
WHERE rm.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = 6735 AND x.`deleted` = b'0');

-- ③-2 持有「按钮（父=页面，祖父=6735）」的角色 → 补根目录 6735
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT DISTINCT rm.`role_id`, 6735, '1', NOW(), '1', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` = rm.`menu_id` AND m.`deleted` = b'0'
JOIN `system_menu` p ON p.`id` = m.`parent_id` AND p.`deleted` = b'0' AND p.`parent_id` = 6735
WHERE rm.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = 6735 AND x.`deleted` = b'0');

-- ========== 校验 ==========
-- 期望：4 行菜单（各权限码 1 个承载）；roles_missing_root = 0
SELECT id, name, permission, type, parent_id
  FROM `system_menu` WHERE id IN (60006300, 60006301, 60006302, 60006303) ORDER BY id;
SELECT permission, COUNT(*) AS carriers FROM `system_menu`
 WHERE permission IN ('bidding:subject-auth:query','bidding:subject-auth:update','bidding:tender-doc:query','bidding:tender-doc:download')
   AND deleted = b'0' GROUP BY permission;
SELECT COUNT(*) AS roles_missing_root
FROM (SELECT DISTINCT rm.role_id FROM `system_role_menu` rm
      JOIN `system_menu` m ON m.id = rm.menu_id AND m.deleted = b'0' AND m.parent_id = 6735
      WHERE rm.deleted = b'0'
        AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id = rm.role_id AND x.menu_id = 6735 AND x.deleted = b'0')) t;