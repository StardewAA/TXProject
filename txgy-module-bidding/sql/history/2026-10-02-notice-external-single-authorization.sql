-- =====================================================================
-- 2026-10-02 外部公告审核 · 单一授权点收敛（替代 update-carrier-restore 的拆分方案）
-- 决策：外部公告审核 = 一个授权点（6736 菜单，permission=bidding:notice-external:update）。
--   勾选 6736 → 可见菜单 + 可看列表 + 可审核（页面管理分页/审核/重建索引接口均为 update 权限，天然一致）；
--   未勾 → 完全不可见（查看/关注场景走独立 query 承载 60006260，不涉及审核菜单）。
--   撤销上一脚本刚建的 60001201 按钮承载（未提交，直接软删 + 清 role_menu）。
-- =====================================================================

USE `ruoyi-vue-pro`;

UPDATE `system_menu` SET `permission` = 'bidding:notice-external:update', `updater` = '1', `update_time` = NOW() WHERE `id` = 6736 AND `deleted` = b'0';

UPDATE `system_menu` SET `deleted` = b'1', `updater` = '1', `update_time` = NOW() WHERE `id` = 60001201 AND `deleted` = b'0';
UPDATE `system_role_menu` SET `deleted` = b'1', `updater` = '1', `update_time` = NOW() WHERE `menu_id` = 60001201 AND `deleted` = b'0';

-- 复核：update 承载 = 6736 唯一；query 承载 = 60006260
SELECT id, name, permission, deleted FROM system_menu WHERE id IN (6736, 60001201) OR permission IN ('bidding:notice-external:update', 'bidding:notice-external:query');