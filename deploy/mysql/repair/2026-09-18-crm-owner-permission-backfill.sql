-- =============================================================================
-- CRM 业务数据「负责人」权限行补齐脚本
--
-- 背景：
--   导入/迁移进来的 CRM 数据，crm_permission 里只有「团队成员」行，缺少负责人本人的
--   level = 1（负责人）权限行。CrmPermissionAspect 校验规则为：
--     1) 若该业务数据一条权限行都没有，则 READ 放行、WRITE 及以上直接报「没有权限」；
--     2) 若当前用户是负责人（level = 1），则默认拥有所有权限；
--     3) 若「我的下级」在该数据上有足够权限，则我也有该权限（主管可操作下属数据）。
--   因此缺少负责人行时，负责人本人（以及其主管）点「转客户 / 编辑 / 转移」都会被拒绝。
--
-- 处理：
--   为每个仍存活、且有负责人的 CRM 业务数据，补齐负责人 level = 1 的权限行。
--
-- 说明：
--   - 幂等：NOT EXISTS 保证重复执行不会产生重复行；
--   - 只补「权限行」，不新增/修改任何业务数据；
--   - biz_type 取值见 CrmBizTypeEnum：1 线索 / 2 客户 / 3 联系人 / 4 商机 / 5 合同
--     / 6 产品 / 7 回款 / 8 回款计划 / 9 报价
-- =============================================================================

-- 1 线索
INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, tenant_id, deleted)
SELECT 1, x.id, x.owner_user_id, 1, '1', NOW(), '1', NOW(), x.tenant_id, b'0'
FROM crm_clue x
WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_permission p
                  WHERE p.biz_type = 1 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0');

-- 2 客户
INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, tenant_id, deleted)
SELECT 2, x.id, x.owner_user_id, 1, '1', NOW(), '1', NOW(), x.tenant_id, b'0'
FROM crm_customer x
WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_permission p
                  WHERE p.biz_type = 2 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0');

-- 3 联系人
INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, tenant_id, deleted)
SELECT 3, x.id, x.owner_user_id, 1, '1', NOW(), '1', NOW(), x.tenant_id, b'0'
FROM crm_contact x
WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_permission p
                  WHERE p.biz_type = 3 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0');

-- 4 商机
INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, tenant_id, deleted)
SELECT 4, x.id, x.owner_user_id, 1, '1', NOW(), '1', NOW(), x.tenant_id, b'0'
FROM crm_business x
WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_permission p
                  WHERE p.biz_type = 4 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0');

-- 5 合同
INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, tenant_id, deleted)
SELECT 5, x.id, x.owner_user_id, 1, '1', NOW(), '1', NOW(), x.tenant_id, b'0'
FROM crm_contract x
WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_permission p
                  WHERE p.biz_type = 5 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0');

-- 6 产品
INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, tenant_id, deleted)
SELECT 6, x.id, x.owner_user_id, 1, '1', NOW(), '1', NOW(), x.tenant_id, b'0'
FROM crm_product x
WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_permission p
                  WHERE p.biz_type = 6 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0');

-- 7 回款
INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, tenant_id, deleted)
SELECT 7, x.id, x.owner_user_id, 1, '1', NOW(), '1', NOW(), x.tenant_id, b'0'
FROM crm_receivable x
WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_permission p
                  WHERE p.biz_type = 7 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0');

-- 8 回款计划
INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, tenant_id, deleted)
SELECT 8, x.id, x.owner_user_id, 1, '1', NOW(), '1', NOW(), x.tenant_id, b'0'
FROM crm_receivable_plan x
WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_permission p
                  WHERE p.biz_type = 8 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0');

-- 9 报价
INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, tenant_id, deleted)
SELECT 9, x.id, x.owner_user_id, 1, '1', NOW(), '1', NOW(), x.tenant_id, b'0'
FROM crm_quotation x
WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_permission p
                  WHERE p.biz_type = 9 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0');

-- =============================================================================
-- 校验：以下「缺负责人行」应全部为 0
-- =============================================================================
SELECT '1 线索' AS biz_type, count(*) AS 缺负责人行 FROM crm_clue x
  WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM crm_permission p WHERE p.biz_type = 1 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0')
UNION ALL SELECT '2 客户', count(*) FROM crm_customer x
  WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM crm_permission p WHERE p.biz_type = 2 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0')
UNION ALL SELECT '3 联系人', count(*) FROM crm_contact x
  WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM crm_permission p WHERE p.biz_type = 3 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0')
UNION ALL SELECT '4 商机', count(*) FROM crm_business x
  WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM crm_permission p WHERE p.biz_type = 4 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0')
UNION ALL SELECT '5 合同', count(*) FROM crm_contract x
  WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM crm_permission p WHERE p.biz_type = 5 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0')
UNION ALL SELECT '6 产品', count(*) FROM crm_product x
  WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM crm_permission p WHERE p.biz_type = 6 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0')
UNION ALL SELECT '7 回款', count(*) FROM crm_receivable x
  WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM crm_permission p WHERE p.biz_type = 7 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0')
UNION ALL SELECT '8 回款计划', count(*) FROM crm_receivable_plan x
  WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM crm_permission p WHERE p.biz_type = 8 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0')
UNION ALL SELECT '9 报价', count(*) FROM crm_quotation x
  WHERE x.deleted = b'0' AND x.owner_user_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM crm_permission p WHERE p.biz_type = 9 AND p.biz_id = x.id AND p.user_id = x.owner_user_id AND p.deleted = b'0');
