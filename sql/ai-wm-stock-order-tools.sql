-- ============================================================================
-- 仓储出入库单据「AI 录入」工具注册与角色绑定（幂等，可重复执行）
--
-- 工具（代码在 txgy-module-ai .../tool/function/mes/，均为「只建草稿」）：
--   mes_wm_item_receipt_create  采购入库单草稿（mes_wm_item_receipt 主单+行+明细）
--   mes_wm_product_issue_create 生产领料单草稿（mes_wm_product_issue 主单+行+明细）
--
-- 做法：不写死 id、按租户动态处理。
--   1) 为「已有 MES 分类 AI 角色」的每个租户各补一条 ai_tool 记录（name 必须与 Bean 名一致）；
--   2) 把这两条工具追加到该租户 MES 角色的 tool_ids（已绑定则不重复追加）。
--
-- 说明：工具能出现在模型面前还有两道闸门（都不在本脚本范围）：
--   - AiPermissionGuard.filterAllowedTools：需要该角色已授权「仓储库存(WM_STOCK)」业务域，
--     写工具跟随查询权限放行（见 AiPermissionGuard）；
--   - 前端 ai_chat_role.tool_ids 必须包含工具 id（本脚本负责）。
-- ============================================================================

SET NAMES utf8mb4;
SET @operator = 'system';

-- ---------- 1. 注册工具（按租户 + 名称判重）----------
INSERT INTO ai_tool (name, description, status, creator, updater, deleted, tenant_id)
SELECT t.name, t.description, 0, @operator, @operator, b'0', s.tenant_id
FROM (SELECT DISTINCT tenant_id FROM ai_chat_role WHERE deleted = b'0' AND category = 'MES') s
         JOIN (
    SELECT 'mes_wm_item_receipt_create' AS name,
           '采购入库单创建：把用户描述的物料/数量/供应商/仓库落成 MES 采购入库单草稿（只建草稿，不提交审批、不入库）' AS description
    UNION ALL
    SELECT 'mes_wm_product_issue_create',
           '生产领料单创建：把用户描述的生产工单/领用物料/数量/仓库落成 MES 生产领料单草稿（只建草稿，不提交审批、不领出）'
) t
WHERE NOT EXISTS (SELECT 1 FROM ai_tool e
                  WHERE e.name = t.name AND e.tenant_id = s.tenant_id AND e.deleted = b'0');

-- ---------- 2. 绑定到该租户的 MES 角色（追加，不覆盖已有工具）----------
-- 注意：必须**一个工具一条 UPDATE**。若写成一条 UPDATE ... JOIN ai_tool（一条角色行会匹配到两条工具行），
-- MySQL 对同一目标行的多行匹配只应用其中一条且不确定是哪条，会导致漏绑。
UPDATE ai_chat_role r
    JOIN ai_tool t ON t.tenant_id = r.tenant_id AND t.deleted = b'0'
        AND t.name = 'mes_wm_item_receipt_create'
SET r.tool_ids = IF(r.tool_ids IS NULL OR r.tool_ids = '',
                    CAST(t.id AS CHAR), CONCAT(r.tool_ids, ',', t.id))
WHERE r.deleted = b'0' AND r.category = 'MES'
  AND (r.tool_ids IS NULL
       OR NOT FIND_IN_SET(CAST(t.id AS CHAR) COLLATE utf8mb4_unicode_ci, r.tool_ids));

UPDATE ai_chat_role r
    JOIN ai_tool t ON t.tenant_id = r.tenant_id AND t.deleted = b'0'
        AND t.name = 'mes_wm_product_issue_create'
SET r.tool_ids = IF(r.tool_ids IS NULL OR r.tool_ids = '',
                    CAST(t.id AS CHAR), CONCAT(r.tool_ids, ',', t.id))
WHERE r.deleted = b'0' AND r.category = 'MES'
  AND (r.tool_ids IS NULL
       OR NOT FIND_IN_SET(CAST(t.id AS CHAR) COLLATE utf8mb4_unicode_ci, r.tool_ids));

-- ---------- 3. 结果确认 ----------
SELECT t.tenant_id, t.id AS tool_id, t.name, t.description
FROM ai_tool t
WHERE t.deleted = b'0' AND t.name IN ('mes_wm_item_receipt_create', 'mes_wm_product_issue_create')
ORDER BY t.tenant_id, t.name;

SELECT r.id AS role_id, r.tenant_id, r.name,
       FIND_IN_SET(CAST((SELECT id FROM ai_tool WHERE name = 'mes_wm_item_receipt_create'
                         AND tenant_id = r.tenant_id AND deleted = b'0' LIMIT 1) AS CHAR)
                   COLLATE utf8mb4_unicode_ci, r.tool_ids) > 0 AS 已绑采购入库,
       FIND_IN_SET(CAST((SELECT id FROM ai_tool WHERE name = 'mes_wm_product_issue_create'
                         AND tenant_id = r.tenant_id AND deleted = b'0' LIMIT 1) AS CHAR)
                   COLLATE utf8mb4_unicode_ci, r.tool_ids) > 0 AS 已绑生产领料
FROM ai_chat_role r
WHERE r.deleted = b'0' AND r.category = 'MES'
ORDER BY r.tenant_id;
