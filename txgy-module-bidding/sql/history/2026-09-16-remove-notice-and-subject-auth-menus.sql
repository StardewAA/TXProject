-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;

-- 2026-09-16 菜单收缩：删除管理端「公告/公示管理」模块 与「主体信息与授权管理」下的「平台授权管理」入口
--
-- 背景（业务方口径）：
--   ① 删除 公告/公示管理（目录 6753 + 页面 6754 + 按钮 6755-6758）；
--   ② 删除 主体信息与授权管理(6759) 下的「平台授权管理」(页面 6762 + 按钮 6763)；
--      仅删该入口，**保留** 6760「主体档案（企业自维护）」与 6761。
--
-- 影响面（已核对，不影响其他模块与门户/员工端）：
--   · 只删招投标(6735)子树内这两处菜单及其角色授权(system_role_menu)，其余菜单/授权不动；
--   · 门户四模块（公告大厅 / 标书制作 / 合规审查 / 投标流程管理）走的是外部公告域、投标流程与文件上传接口，
--     不使用 bidding:notice:* 与 bidding:subject-auth:* 权限码，故不受影响；
--   · 门户「本平台发布」读的是 /bidding/notice/public/* （@PermitAll，公开读），与菜单/权限码无关，不受影响。
--
-- 删除方式：**物理删除（DELETE）**，不用软删(deleted=1)。
--   原因：初始包 sql/master-bidding-sync.sql 由 sql/tools/build-init-from-db.js 按 id 区间(6735~6799)
--   从开发库 mysqldump 导出，软删行仍会被导出；物理删除才能保证重生成的初始包不再含这两个菜单。
--
-- 执行：root@ruoyi-vue-pro 库（system_menu / system_role_menu 同库）；幂等可重跑。

-- 1) 先删角色授权（system_role_menu），避免留下指向已删菜单的悬挂行
DELETE FROM system_role_menu
WHERE menu_id IN (6753, 6754, 6755, 6756, 6757, 6758, 6762, 6763);

-- 2) 再删菜单本体（system_menu）
--    公告/公示管理：6753 目录 + 6754 页面 + 6755-6758 按钮
--    平台授权管理：6762 页面 + 6763 按钮
DELETE FROM system_menu
WHERE id IN (6753, 6754, 6755, 6756, 6757, 6758, 6762, 6763);

-- 3) 校验
--    期望：公告/公示管理 与 平台授权管理 均已不存在（0 / 0）
SELECT COUNT(*) AS notice_module_left FROM system_menu WHERE id IN (6753, 6754, 6755, 6756, 6757, 6758);
SELECT COUNT(*) AS subject_auth_left  FROM system_menu WHERE id IN (6762, 6763);
--    期望：主体信息与授权管理目录及其下「主体档案（企业自维护）」保留（3 行：6759/6760/6761）
SELECT id, name, permission FROM system_menu WHERE id IN (6759, 6760, 6761) AND deleted = 0;
