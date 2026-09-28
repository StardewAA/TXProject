-- ============================================================
-- CMS 菜单定版：五大模块（按顺序）
--   1 智能检索  2 审查管理  3 发布管理  4 内容管理  5 专家管理
--   删除：咨询服务、统计分析（逻辑删除，可恢复）
-- 幂等：可重复执行
-- ============================================================

-- 1) 恢复专家管理及其子菜单（聘请专家/专家信息维护/咨询订单管理/结算管理）
UPDATE system_menu SET deleted = b'0', visible = b'1'
WHERE id = 70011 OR parent_id = 70011;

-- 2) 删除多余的一级模块：咨询服务、统计分析（含子菜单）
UPDATE system_menu SET deleted = b'1'
WHERE deleted = b'0' AND (id IN (70016, 70025) OR parent_id IN (70016, 70025));

-- 3) 固定一级模块顺序
UPDATE system_menu SET sort = 1 WHERE id = 70029;   -- 智能检索
UPDATE system_menu SET sort = 2 WHERE id = 70007;   -- 审查管理
UPDATE system_menu SET sort = 3 WHERE id = 70030;   -- 发布管理
UPDATE system_menu SET sort = 4 WHERE id = 70040;   -- 内容管理
UPDATE system_menu SET sort = 5 WHERE id = 70011;   -- 专家管理
