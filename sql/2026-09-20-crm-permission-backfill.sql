-- =============================================================================
-- 补齐 CRM 各业务的「负责人数据权限记录」（crm_permission）
--
-- 【问题】
--   crm_permission 是 CRM 数据权限的权威表，切面 CrmPermissionAspect 完全依赖它。
--   历史数据（部分是用脚本直接导入的）没有同步插入该表，导致出现这种诡异现象：
--       「列表里能看到这条数据，但点开详情 / 编辑 / 写跟进却提示没有权限」
--   实测缺失：线索 17 条、客户 2 条、商机 5 条、合同 3 条。
--
-- 【方案】
--   给每张 CRM 业务表中「负责人不为空」且「该负责人尚无权限记录」的数据，
--   补一条 level = 1（负责人 OWNER）的记录（OWNER 在切面里拥有全部权限）。
--
--   biz_type 对照（CrmBizTypeEnum）：
--       1 线索  2 客户  3 联系人  4 商机  5 合同  6 产品  7 回款  8 回款计划  9 报价
--
-- 【特性】幂等，可重复执行（按 biz_type + biz_id + user_id 去重，不依赖唯一索引）。
-- 【执行】mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
-- =============================================================================

INSERT INTO crm_permission (biz_type, biz_id, user_id, level, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT t.biz_type, t.biz_id, t.owner_user_id, 1, '1', NOW(), '1', NOW(), b'0', t.tenant_id
FROM (
              SELECT 1 AS biz_type, id AS biz_id, owner_user_id, tenant_id FROM crm_clue            WHERE deleted = 0
    UNION ALL SELECT 2, id, owner_user_id, tenant_id FROM crm_customer      WHERE deleted = 0
    UNION ALL SELECT 3, id, owner_user_id, tenant_id FROM crm_contact       WHERE deleted = 0
    UNION ALL SELECT 4, id, owner_user_id, tenant_id FROM crm_business      WHERE deleted = 0
    UNION ALL SELECT 5, id, owner_user_id, tenant_id FROM crm_contract      WHERE deleted = 0
    UNION ALL SELECT 6, id, owner_user_id, tenant_id FROM crm_product       WHERE deleted = 0
    UNION ALL SELECT 7, id, owner_user_id, tenant_id FROM crm_receivable    WHERE deleted = 0
    UNION ALL SELECT 8, id, owner_user_id, tenant_id FROM crm_receivable_plan WHERE deleted = 0
    UNION ALL SELECT 9, id, owner_user_id, tenant_id FROM crm_quotation     WHERE deleted = 0
) t
WHERE t.owner_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM crm_permission p
      WHERE p.biz_type = t.biz_type
        AND p.biz_id = t.biz_id
        AND p.user_id = t.owner_user_id
        AND p.deleted = 0
  );

-- 自查：应返回 0
SELECT COUNT(*) AS still_missing
FROM (
              SELECT 1 AS biz_type, id AS biz_id, owner_user_id FROM crm_clue            WHERE deleted = 0
    UNION ALL SELECT 2, id, owner_user_id FROM crm_customer      WHERE deleted = 0
    UNION ALL SELECT 3, id, owner_user_id FROM crm_contact       WHERE deleted = 0
    UNION ALL SELECT 4, id, owner_user_id FROM crm_business      WHERE deleted = 0
    UNION ALL SELECT 5, id, owner_user_id FROM crm_contract      WHERE deleted = 0
    UNION ALL SELECT 6, id, owner_user_id FROM crm_product       WHERE deleted = 0
    UNION ALL SELECT 7, id, owner_user_id FROM crm_receivable    WHERE deleted = 0
    UNION ALL SELECT 8, id, owner_user_id FROM crm_receivable_plan WHERE deleted = 0
    UNION ALL SELECT 9, id, owner_user_id FROM crm_quotation     WHERE deleted = 0
) t
WHERE t.owner_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM crm_permission p
      WHERE p.biz_type = t.biz_type
        AND p.biz_id = t.biz_id
        AND p.user_id = t.owner_user_id
        AND p.deleted = 0
  );
