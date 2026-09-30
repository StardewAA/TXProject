-- ============================================================
-- 售后示例数据回滚脚本
-- 生成时间：2026-09-23
-- 作用：删除本次插入的全部示例数据，恢复到写入前状态
-- 说明：按主键精确删除并按「先子表、后父表」顺序执行
--       写入前的完整备份见 crm_after_sale_backup_20260923.sql
-- ============================================================
SET NAMES utf8mb4;

-- ⑤ 问题工单（4 条）
DELETE FROM crm_after_sale_ticket
 WHERE tenant_id = 2012 AND id IN (1, 2, 3, 4);

-- ④ 回访记录（3 条）
DELETE FROM crm_after_sale_visit_record
 WHERE tenant_id = 2012 AND id IN (4, 5, 6);

-- ③ 回访任务（6 条）
DELETE FROM crm_after_sale_visit_task
 WHERE tenant_id = 2012 AND id IN (10, 11, 13, 14, 15, 16);

-- ② 回访计划（3 条）
DELETE FROM crm_after_sale_visit_plan
 WHERE tenant_id = 2012 AND id IN (3, 4, 5);

-- ① 质保档案（3 条）
DELETE FROM crm_after_sale_warranty
 WHERE tenant_id = 2012 AND id IN (8, 9, 10);

-- 校验：以下结果应全部为 0
SELECT 'warranty' AS tbl, COUNT(*) AS left_rows FROM crm_after_sale_warranty
 WHERE tenant_id = 2012 AND id IN (8, 9, 10)
UNION ALL SELECT 'visit_plan', COUNT(*) FROM crm_after_sale_visit_plan
 WHERE tenant_id = 2012 AND id IN (3, 4, 5)
UNION ALL SELECT 'visit_task', COUNT(*) FROM crm_after_sale_visit_task
 WHERE tenant_id = 2012 AND id IN (10, 11, 13, 14, 15, 16)
UNION ALL SELECT 'visit_record', COUNT(*) FROM crm_after_sale_visit_record
 WHERE tenant_id = 2012 AND id IN (4, 5, 6)
UNION ALL SELECT 'ticket', COUNT(*) FROM crm_after_sale_ticket
 WHERE tenant_id = 2012 AND id IN (1, 2, 3, 4);
