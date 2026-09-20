-- =====================================================================
-- 修复：HR 角色「招聘沟通」菜单授权被软删除 + 「转为候选人」菜单名称为空
--
-- 现象（2026-09-15 测试发现）：
--   1) 秦川租户 HR 账号（qinchuan_hr / hr_role_2020）打开 HRM-招聘沟通提示
--      「没有该操作权限」，接口 /hrm/recruit/chat/conversation/list 返回 403；
--   2) 由于 HR 无法回复，求职者也被 1050200065「需等待招聘方回复后才能投递简历」拦住；
--   3) 菜单 1647 名称为空（原 SQL 用十六进制字面量拼写错误）。
--
-- 原因：
--   system_role_menu 中 role_id in (60468..60472)、menu_id in (1641,1646,1647)
--   的授权行 deleted=1（软删除），只有 60467（hr_role_2019）为 0；
--   system_menu.id=1647 的 name 在写入时被转成空串。
--
-- 幂等：全部使用 UPDATE ... WHERE 条件，可重复执行。
-- 生成日期：2026-09-15
-- =====================================================================

SET NAMES utf8mb4;

-- 1. 修正「转为候选人」菜单名称（原插入语句的十六进制字符串有误）
UPDATE system_menu
SET name = '转为候选人', update_time = NOW()
WHERE id = 1647
  AND (name IS NULL OR name = '');

-- 2. 恢复「招聘沟通 / 招聘沟通删除 / 转为候选人」以及「政策制度」菜单的角色授权
--    （2026-09-15 实测：hr_role_2020 对 1641/1646/1647、1661-1665 的授权均为 deleted=1）
UPDATE system_role_menu
SET deleted = b'0', update_time = NOW(), updater = 'repair-20260915'
WHERE deleted <> b'0'
  AND (
      menu_id IN (1641, 1642, 1643, 1644, 1645, 1646, 1647, 1661, 1662, 1663, 1664, 1665)
      OR role_id IN (SELECT id FROM system_role WHERE code LIKE 'hr_role_%' AND deleted = b'0')
  );

-- 3. 容错：若某些 HR 角色完全没有授权行，则按「已授权 1641 的角色」补齐 1646/1647
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT source.role_id, missing.menu_id, 'repair-20260915', NOW(), 'repair-20260915', NOW(), b'0', source.tenant_id
FROM system_role_menu source
JOIN (SELECT 1646 AS menu_id UNION ALL SELECT 1647) missing
JOIN system_role role ON role.id = source.role_id AND role.code LIKE 'hr_role_%' AND role.deleted = b'0'
WHERE source.menu_id = 1641
  AND source.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu existing
      WHERE existing.role_id = source.role_id
        AND existing.menu_id = missing.menu_id
        AND existing.tenant_id = source.tenant_id
        AND existing.deleted = b'0'
  );

-- 4. 核验查询（执行后可人工确认）
-- SELECT rm.menu_id, COUNT(*) FROM system_role_menu rm WHERE rm.menu_id IN (1641,1646,1647) AND rm.deleted = b'0' GROUP BY rm.menu_id;
--
-- 5. 重要：权限判定使用 Redis 缓存（permission_menu_ids / menu_role_ids / user_role_ids，TTL 约 4 分钟），
--    修复数据库后需要清理相关缓存，否则接口仍会返回 403。示例（容器内执行）：
--    redis-cli --scan --pattern "permission_menu_ids:hrm:*" | xargs -r redis-cli DEL
--    redis-cli --scan --pattern "menu_role_ids:*" | xargs -r redis-cli DEL
--    redis-cli --scan --pattern "user_role_ids:*" | xargs -r redis-cli DEL
