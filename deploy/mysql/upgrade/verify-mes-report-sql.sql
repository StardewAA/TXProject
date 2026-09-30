-- 报表模板数据集 SQL 验证（以只读账号执行，只看行数）
SELECT 'work_order_completion' AS report, COUNT(*) AS rows_cnt FROM (
  SELECT w.code AS work_order_code, i.name AS item_name, w.quantity AS plan_quantity, w.quantity_produced AS produced_quantity, ROUND(CASE WHEN w.quantity > 0 THEN w.quantity_produced / w.quantity * 100 ELSE 0 END, 2) AS progress_percent, CASE w.status WHEN 0 THEN '草稿' WHEN 1 THEN '已确认' WHEN 2 THEN '已完成' WHEN 3 THEN '已取消' WHEN 10 THEN '已下发' WHEN 11 THEN '生产中' ELSE '其他' END AS status_name, DATE_FORMAT(w.request_date, '%Y-%m-%d') AS request_date, DATE_FORMAT(w.finish_date, '%Y-%m-%d') AS finish_date FROM mes_pro_work_order w LEFT JOIN mes_md_item i ON i.id = w.product_id WHERE w.deleted = 0 AND w.tenant_id = 1 ORDER BY w.request_date DESC
) t1;

SELECT 'defect_summary' AS report, COUNT(*) AS rows_cnt FROM (
  SELECT i.code AS item_code, i.name AS item_name, p.name AS process_name, SUM(f.unqualified_quantity) AS unqualified_quantity, SUM(f.labor_scrap_quantity) AS labor_scrap_quantity, SUM(f.material_scrap_quantity) AS material_scrap_quantity, SUM(f.other_scrap_quantity) AS other_scrap_quantity FROM mes_pro_feedback f LEFT JOIN mes_md_item i ON i.id = f.item_id LEFT JOIN mes_pro_process p ON p.id = f.process_id WHERE f.deleted = 0 AND f.status IN (2, 3, 4) AND f.tenant_id = 1 GROUP BY i.code, i.name, p.name ORDER BY unqualified_quantity DESC
) t2;

SELECT 'material_consumption' AS report, COUNT(*) AS rows_cnt FROM (
  SELECT w.code AS work_order_code, p.name AS process_name, DATE_FORMAT(c.consume_date, '%Y-%m-%d') AS consume_date, i.code AS item_code, i.name AS item_name, SUM(d.quantity) AS consume_quantity FROM mes_wm_item_consume_detail d JOIN mes_wm_item_consume c ON c.id = d.consume_id LEFT JOIN mes_pro_work_order w ON w.id = c.work_order_id LEFT JOIN mes_pro_process p ON p.id = c.process_id LEFT JOIN mes_md_item i ON i.id = d.item_id WHERE d.deleted = 0 AND c.deleted = 0 AND c.tenant_id = 1 GROUP BY w.code, p.name, DATE_FORMAT(c.consume_date, '%Y-%m-%d'), i.code, i.name ORDER BY consume_date DESC
) t3;

SELECT 'machinery_efficiency' AS report, COUNT(*) AS rows_cnt FROM (
  SELECT m.code AS machinery_code, m.name AS machinery_name, COUNT(r.id) AS repair_count, SUM(CASE WHEN r.finish_date IS NOT NULL AND r.require_date IS NOT NULL THEN TIMESTAMPDIFF(HOUR, r.require_date, r.finish_date) ELSE 0 END) AS repair_hours, DATE_FORMAT(m.last_check_time, '%Y-%m-%d %H:%i') AS last_check_time, DATE_FORMAT(m.last_mainten_time, '%Y-%m-%d %H:%i') AS last_mainten_time FROM mes_dv_machinery m LEFT JOIN mes_dv_repair r ON r.machinery_id = m.id AND r.deleted = 0 WHERE m.deleted = 0 AND m.tenant_id = 1 GROUP BY m.code, m.name, m.last_check_time, m.last_mainten_time ORDER BY repair_hours DESC
) t4;

SELECT 'feedback_detail' AS report, COUNT(*) AS rows_cnt FROM (
  SELECT f.code AS feedback_code, w.code AS work_order_code, p.name AS process_name, i.name AS item_name, f.feedback_quantity, f.qualified_quantity, f.unqualified_quantity, DATE_FORMAT(f.feedback_time, '%Y-%m-%d %H:%i:%s') AS feedback_time FROM mes_pro_feedback f LEFT JOIN mes_pro_work_order w ON w.id = f.work_order_id LEFT JOIN mes_pro_process p ON p.id = f.process_id LEFT JOIN mes_md_item i ON i.id = f.item_id WHERE f.deleted = 0 AND f.status IN (2, 3, 4) AND f.tenant_id = 1 ORDER BY f.feedback_time DESC
) t5;
