-- ============================================================
-- ERP 模拟数据种子（tenant_id = 1）
-- 用途：P3 全自动链验收（销售订单审批 -> MES 自动建项/工单/任务）
-- 关键约束：erp_product.name 必须与 mes_md_item.name 完全一致
-- 回滚：按 creator='mock-erp-seed' 精准删除
-- ============================================================

-- 1. 产品分类
INSERT INTO erp_product_category (id, parent_id, name, code, sort, status, creator, tenant_id, deleted)
VALUES
  (1, 0, '防腐保温管成品', 'FG', 1, 0, 'mock-erp-seed', 1, b'0'),
  (2, 0, '管材原材料',     'RM', 2, 0, 'mock-erp-seed', 1, b'0');

-- 2. 客户（与 MES 客户档案同名，叙事一致）
INSERT INTO erp_customer (partner_code, name, contact, mobile, status, sort, creator, tenant_id, deleted)
VALUES
  ('ERP-C-001', '中石油管道-01', '刘经理', '13905320001', 0, 1, 'mock-erp-seed', 1, b'0'),
  ('ERP-C-002', '万华化学-03',   '陈经理', '13905320003', 0, 2, 'mock-erp-seed', 1, b'0');

-- 3. 产品（名称与 MES 物料管理中的物料严格一致，MES 同步按名称匹配）
INSERT INTO erp_product (material_code, material_type, quality_required, name, bar_code, category_id, unit_id, status, standard, purchase_price, sale_price, creator, tenant_id, deleted)
VALUES
  ('M-FG-001', 'FINISHED_GOODS', b'0', '预制保温管-08', 'BC-FG-001', 1, 1, 0, 'DN100 PN1.6MPa 聚氨酯保温', 168.000000, 288.000000, 'mock-erp-seed', 1, b'0'),
  ('M-RM-001', 'RAW_MATERIAL',   b'0', '钢管-01',       'BC-RM-001', 2, 1, 0, 'DN100*6mm Q235B',          86.000000,  128.000000, 'mock-erp-seed', 1, b'0');
