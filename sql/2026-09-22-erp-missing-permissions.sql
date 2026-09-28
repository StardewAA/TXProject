-- ============================================================================
-- 补齐 ERP 后端已引用、但 system_menu 中缺失的权限点
--
-- 背景：ERP 模块的 @PreAuthorize 已引用下列 7 个权限码，但菜单表没有对应
--       权限点，导致「非超级管理员」角色调用时统一返回 403。受影响功能：
--         · 采购订单 / 销售订单 / 采购申请 / 询价 / 定标 / 造价结果 的「反审批」
--           （反审批是本分支 ERP-approval-management 的核心能力）
--         · 供应链协同单「向 WMS 申请入库」
--
-- 代码审查修正（2026-09-27）：
--   1) 移除「商城采购需求」的 4 个孤儿权限点（erp:mall-purchase-demand:*）。
--      该模块 Java 代码已随「移除商城采购需求模块」一并删除，权限点不再有
--      任何后端接口引用，保留会形成无主权限、误导后续权限审计。
--   2) INSERT 增加「主键未被占用」检查：原实现仅按 permission 判重，若目标
--      ID（27101-27107）已被其它记录（含软删除记录）占用，主键冲突会导致
--      整批 INSERT 失败。现同时校验 permission 与 id。
--   3) 「反审批」属高敏感操作（可撤销已生效的审批结果），不再无条件继承
--      父菜单授权，仅自动授予管理员类角色（code LIKE '%admin%'）；其余角色
--      请由运维在脚本执行后按实际岗位分工手工分配。
--
-- 说明：幂等脚本，可重复执行。仅新增缺失项，不覆盖已有数据。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 新增缺失的权限点（type = 3，即按钮级权限）
-- ---------------------------------------------------------------------------
INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT t.id, t.name, t.permission, 3, t.sort, t.parent_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'
FROM (
            SELECT 27101 AS id, '采购订单反审批' AS name, 'erp:purchase-order:reverse-approve' AS permission, 7 AS sort, 2666 AS parent_id
  UNION ALL SELECT 27102, '销售订单反审批',   'erp:sale-order:reverse-approve',        7, 2638
  UNION ALL SELECT 27103, '采购申请反审批',   'erp:purchase-request:reverse-approve',  7, 6773
  UNION ALL SELECT 27104, '询价反审批',       'erp:inquiry:reverse-approve',           9, 6774
  UNION ALL SELECT 27105, '定标反审批',       'erp:award:reverse-approve',             5, 6775
  UNION ALL SELECT 27106, '造价结果反审批',   'erp:cost-result:reverse-approve',       7, 6776
  UNION ALL SELECT 27107, '协同单申请入库',   'erp:collaboration-order:apply-inbound', 5, 6801
) t
LEFT JOIN system_menu m
       ON m.permission = t.permission
      AND m.deleted = b'0'
WHERE m.id IS NULL
  -- 修正 2：目标主键可能已被占用（含软删除记录），避免主键冲突导致整批失败
  AND NOT EXISTS (SELECT 1 FROM system_menu k WHERE k.id = t.id);

-- ---------------------------------------------------------------------------
-- 2a. 常规权限自动授权：把「协同单申请入库」授予已拥有其父菜单的角色（含租户维度）
-- ---------------------------------------------------------------------------
INSERT INTO system_role_menu
  (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, nm.new_id, 'system', NOW(), 'system', NOW(), b'0', rm.tenant_id
FROM system_role_menu rm
JOIN (SELECT 6801 AS parent_id, 27107 AS new_id) nm ON nm.parent_id = rm.menu_id
LEFT JOIN system_role_menu x
       ON x.role_id = rm.role_id
      AND x.menu_id = nm.new_id
      AND x.tenant_id = rm.tenant_id
      AND x.deleted = b'0'
WHERE rm.deleted = b'0'
  AND x.id IS NULL;

-- ---------------------------------------------------------------------------
-- 2b. 敏感权限授权（修正 3）：6 个「反审批」仅自动授予管理员类角色
--     其余角色不自动继承 —— 请运维按实际岗位分工手工分配后再放行
-- ---------------------------------------------------------------------------
INSERT INTO system_role_menu
  (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, nm.new_id, 'system', NOW(), 'system', NOW(), b'0', rm.tenant_id
FROM system_role_menu rm
JOIN (
            SELECT 2666 AS parent_id, 27101 AS new_id
  UNION ALL SELECT 2638, 27102
  UNION ALL SELECT 6773, 27103
  UNION ALL SELECT 6774, 27104
  UNION ALL SELECT 6775, 27105
  UNION ALL SELECT 6776, 27106
) nm ON nm.parent_id = rm.menu_id
JOIN system_role r
       ON r.id = rm.role_id
      AND r.deleted = b'0'
      AND r.code LIKE '%admin%'
LEFT JOIN system_role_menu x
       ON x.role_id = rm.role_id
      AND x.menu_id = nm.new_id
      AND x.tenant_id = rm.tenant_id
      AND x.deleted = b'0'
WHERE rm.deleted = b'0'
  AND x.id IS NULL;

-- ---------------------------------------------------------------------------
-- 3. 执行后校验
-- ---------------------------------------------------------------------------
-- 3.1 权限点应恰好 7 条（无重复、无孤儿）
-- SELECT permission, COUNT(*) FROM system_menu
--  WHERE permission LIKE 'erp:%reverse-approve' OR permission = 'erp:collaboration-order:apply-inbound'
--  GROUP BY permission;
-- 3.2 不应存在商城采购需求的孤儿权限（预期 0 行）
-- SELECT id, name, permission FROM system_menu
--  WHERE permission LIKE 'erp:mall-purchase-demand%' AND deleted = b'0';
