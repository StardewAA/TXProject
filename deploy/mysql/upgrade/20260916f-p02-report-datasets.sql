-- =====================================================================
-- 2026-09-16 报表体系 P0-2：核心模板数据集（新增 4 张 + 下线 2 张误登记）
-- 背景：P0-1 已登记 5 个 MES 数据集（生产日报/工单完工/不良统计/物料耗用/设备统计，
--       id 1,3,4,5,6,7），加上报工明细台账后覆盖 P0 清单 #1/#2/#5/#10。
--       本脚本补齐 MES/WMS 域 4 张：工序产出、质检合格率、库存收发存、出入库台账。
--       P0 清单 #8/#9（采购执行/销售回款）属 ERP 域含金额，按 20260916b 决策
--       不入 MES 报表中心，由 ERP 负责人登记；此前误登记的 id 12/13 在此软删。
-- 口径来源（均以 DO/枚举为准）：
--   工单状态   MesProWorkOrderStatusEnum   0草稿 1已确认 2已完成 3已取消 10已下发 11生产中
--   报工状态   MesProFeedbackStatusEnum    0草稿 2审批中 3待检验 4已完成
--   质检状态   MesQcStatusEnum             0草稿 4已完成；check_result 1通过 2不通过
--   WMS 单据   各 MesWm*StatusEnum（见各段 CASE）
--   ERP 审核   ErpAuditStatus              0草稿 10审批中 20审批通过 30审批驳回 40已撤销
-- 约定：SQL 必须把 tenant_id 列进 SELECT，租户过滤由服务端强制追加；
--       状态值统一在 SQL 内转中文（报表中心/导出直接可读）。
-- 说明：幂等，按 id 覆盖。id 使用 8~13 段。
-- =====================================================================

SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO `report_dataset`
(`id`, `code`, `name`, `category`, `sql_text`, `param_definitions`, `tenant_column`,
 `max_rows`, `timeout_seconds`, `status`, `owner`, `remark`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`, `tenant_id`, `column_labels`)
VALUES

-- ---------------------------------------------------------------------
-- 8. 工序产出汇总表（P0 #3：行=工序，产量/合格/合格率）
-- ---------------------------------------------------------------------
(8, 'mes_process_output', '工序产出汇总表', 'MES',
 'SELECT f.tenant_id AS tenant_id, p.name AS process_name, COUNT(DISTINCT f.work_order_id) AS work_order_count, COUNT(*) AS record_count, SUM(f.feedback_quantity) AS feedback_quantity, SUM(f.qualified_quantity) AS qualified_quantity, SUM(f.unqualified_quantity) AS unqualified_quantity, ROUND(CASE WHEN SUM(f.feedback_quantity) > 0 THEN SUM(f.qualified_quantity) / SUM(f.feedback_quantity) * 100 ELSE 0 END, 2) AS pass_rate FROM mes_pro_feedback f LEFT JOIN mes_pro_process p ON p.id = f.process_id WHERE f.deleted = 0 AND f.status IN (2, 3, 4) AND f.feedback_time >= ${startDate} AND f.feedback_time < DATE_ADD(${endDate}, INTERVAL 1 DAY) GROUP BY f.tenant_id, p.name ORDER BY SUM(f.feedback_quantity) DESC',
 '[{"name":"startDate","label":"开始日期","type":"date","required":false,"defaultValue":"2000-01-01"},{"name":"endDate","label":"结束日期","type":"date","required":false,"defaultValue":"2099-12-31"}]',
 'tenant_id', 5000, 30, 0, '刘洋', '工序产出汇总：按工序统计产量、合格率（P0 #3）',
 'admin', NOW(), 'admin', NOW(), b'0', 0,
 '[{"field":"process_name","label":"工序"},{"field":"work_order_count","label":"工单数"},{"field":"record_count","label":"报工笔数"},{"field":"feedback_quantity","label":"报工数量"},{"field":"qualified_quantity","label":"合格数量"},{"field":"unqualified_quantity","label":"不合格数量"},{"field":"pass_rate","label":"合格率(%)"}]'),

-- ---------------------------------------------------------------------
-- 9. 质量检验合格率表（P0 #4：UNION IQC/IPQC/OQC/RQC）
-- ---------------------------------------------------------------------
(9, 'mes_qc_pass_rate', '质量检验合格率表', 'MES',
 'SELECT t.tenant_id AS tenant_id, t.qc_type AS qc_type, t.qc_code AS qc_code, t.item_name AS item_name, t.partner_name AS partner_name, t.check_quantity AS check_quantity, t.qualified_quantity AS qualified_quantity, t.unqualified_quantity AS unqualified_quantity, ROUND(CASE WHEN t.check_quantity > 0 THEN t.qualified_quantity / t.check_quantity * 100 ELSE 0 END, 2) AS pass_rate, t.check_result_name AS check_result_name, t.status_name AS status_name, t.inspect_date AS inspect_date FROM ( SELECT ''IQC来料'' AS qc_type, q.code AS qc_code, i.name AS item_name, v.name AS partner_name, q.check_quantity, q.qualified_quantity, q.unqualified_quantity, CASE q.check_result WHEN 1 THEN ''通过'' WHEN 2 THEN ''不通过'' ELSE ''待检'' END AS check_result_name, CASE q.status WHEN 0 THEN ''草稿'' WHEN 4 THEN ''已完成'' ELSE ''其他'' END AS status_name, DATE_FORMAT(q.inspect_date, ''%Y-%m-%d'') AS inspect_date, q.tenant_id AS tenant_id FROM mes_qc_iqc q LEFT JOIN mes_md_item i ON i.id = q.item_id LEFT JOIN mes_md_vendor v ON v.id = q.vendor_id WHERE q.deleted = 0 UNION ALL SELECT ''IPQC过程'' AS qc_type, q.code, i.name, w.code, q.check_quantity, q.qualified_quantity, q.unqualified_quantity, CASE q.check_result WHEN 1 THEN ''通过'' WHEN 2 THEN ''不通过'' ELSE ''待检'' END, CASE q.status WHEN 0 THEN ''草稿'' WHEN 4 THEN ''已完成'' ELSE ''其他'' END, DATE_FORMAT(q.inspect_date, ''%Y-%m-%d''), q.tenant_id FROM mes_qc_ipqc q LEFT JOIN mes_md_item i ON i.id = q.item_id LEFT JOIN mes_pro_work_order w ON w.id = q.work_order_id WHERE q.deleted = 0 UNION ALL SELECT ''OQC出货'' AS qc_type, q.code, i.name, c.name, q.check_quantity, q.qualified_quantity, q.unqualified_quantity, CASE q.check_result WHEN 1 THEN ''通过'' WHEN 2 THEN ''不通过'' ELSE ''待检'' END, CASE q.status WHEN 0 THEN ''草稿'' WHEN 4 THEN ''已完成'' ELSE ''其他'' END, DATE_FORMAT(q.inspect_date, ''%Y-%m-%d''), q.tenant_id FROM mes_qc_oqc q LEFT JOIN mes_md_item i ON i.id = q.item_id LEFT JOIN mes_md_client c ON c.id = q.client_id WHERE q.deleted = 0 UNION ALL SELECT ''RQC退货'' AS qc_type, q.code, i.name, q.batch_code, q.check_quantity, q.qualified_quantity, q.unqualified_quantity, CASE q.check_result WHEN 1 THEN ''通过'' WHEN 2 THEN ''不通过'' ELSE ''待检'' END, CASE q.status WHEN 0 THEN ''草稿'' WHEN 4 THEN ''已完成'' ELSE ''其他'' END, DATE_FORMAT(q.inspect_date, ''%Y-%m-%d''), q.tenant_id FROM mes_qc_rqc q LEFT JOIN mes_md_item i ON i.id = q.item_id WHERE q.deleted = 0 ) t WHERE t.inspect_date >= ${startDate} AND t.inspect_date < DATE_ADD(${endDate}, INTERVAL 1 DAY) ORDER BY t.inspect_date DESC',
 '[{"name":"startDate","label":"开始日期","type":"date","required":false,"defaultValue":"2000-01-01"},{"name":"endDate","label":"结束日期","type":"date","required":false,"defaultValue":"2099-12-31"}]',
 'tenant_id', 5000, 30, 0, '刘洋', '质检合格率：IQC/IPQC/OQC/RQC 四类检验单统一口径（P0 #4）',
 'admin', NOW(), 'admin', NOW(), b'0', 0,
 '[{"field":"qc_type","label":"检验类型"},{"field":"qc_code","label":"检验单号"},{"field":"item_name","label":"物料名称"},{"field":"partner_name","label":"供应商/工单/客户/批次"},{"field":"check_quantity","label":"检验数量"},{"field":"qualified_quantity","label":"合格数量"},{"field":"unqualified_quantity","label":"不合格数量"},{"field":"pass_rate","label":"合格率(%)"},{"field":"check_result_name","label":"检验结果"},{"field":"status_name","label":"单据状态"},{"field":"inspect_date","label":"检验日期"}]'),

-- ---------------------------------------------------------------------
-- 10. 库存收发存汇总表（P0 #6：按物料，在库 + 期间入库/出库）
--     期间入库 = 采购入库 + 产品入库 + 杂项入库；期间出库 = 领料出库 + 杂项出库
-- ---------------------------------------------------------------------
(10, 'mes_wm_stock_flow', '库存收发存汇总表（按物料）', 'WMS',
 'SELECT s.tenant_id AS tenant_id, i.code AS item_code, i.name AS item_name, IFNULL(inb.in_quantity, 0) AS in_quantity, IFNULL(outb.out_quantity, 0) AS out_quantity, SUM(s.quantity) AS stock_quantity FROM mes_wm_material_stock s LEFT JOIN mes_md_item i ON i.id = s.item_id LEFT JOIN ( SELECT r.tenant_id, l.item_id, SUM(l.received_quantity) AS in_quantity FROM mes_wm_item_receipt_line l JOIN mes_wm_item_receipt r ON r.id = l.receipt_id AND r.deleted = 0 AND r.receipt_date >= ${startDate} AND r.receipt_date < DATE_ADD(${endDate}, INTERVAL 1 DAY) WHERE l.deleted = 0 GROUP BY r.tenant_id, l.item_id UNION ALL SELECT r.tenant_id, l.item_id, SUM(l.quantity) FROM mes_wm_product_receipt_line l JOIN mes_wm_product_receipt r ON r.id = l.receipt_id AND r.deleted = 0 AND r.receipt_date >= ${startDate} AND r.receipt_date < DATE_ADD(${endDate}, INTERVAL 1 DAY) WHERE l.deleted = 0 GROUP BY r.tenant_id, l.item_id UNION ALL SELECT r.tenant_id, l.item_id, SUM(l.quantity) FROM mes_wm_misc_receipt_line l JOIN mes_wm_misc_receipt r ON r.id = l.receipt_id AND r.deleted = 0 AND r.receipt_date >= ${startDate} AND r.receipt_date < DATE_ADD(${endDate}, INTERVAL 1 DAY) WHERE l.deleted = 0 GROUP BY r.tenant_id, l.item_id ) inb ON inb.tenant_id = s.tenant_id AND inb.item_id = s.item_id LEFT JOIN ( SELECT r.tenant_id, l.item_id, SUM(l.quantity) AS out_quantity FROM mes_wm_product_issue_line l JOIN mes_wm_product_issue r ON r.id = l.issue_id AND r.deleted = 0 AND r.issue_date >= ${startDate} AND r.issue_date < DATE_ADD(${endDate}, INTERVAL 1 DAY) WHERE l.deleted = 0 GROUP BY r.tenant_id, l.item_id UNION ALL SELECT r.tenant_id, l.item_id, SUM(l.quantity) FROM mes_wm_misc_issue_line l JOIN mes_wm_misc_issue r ON r.id = l.issue_id AND r.deleted = 0 AND r.issue_date >= ${startDate} AND r.issue_date < DATE_ADD(${endDate}, INTERVAL 1 DAY) WHERE l.deleted = 0 GROUP BY r.tenant_id, l.item_id ) outb ON outb.tenant_id = s.tenant_id AND outb.item_id = s.item_id WHERE s.deleted = 0 GROUP BY s.tenant_id, i.code, i.name, inb.in_quantity, outb.out_quantity ORDER BY stock_quantity DESC',
 '[{"name":"startDate","label":"开始日期","type":"date","required":false,"defaultValue":"2000-01-01"},{"name":"endDate","label":"结束日期","type":"date","required":false,"defaultValue":"2099-12-31"}]',
 'tenant_id', 5000, 30, 0, '刘洋', '库存收发存：在库余量 + 期间入库/出库（采购/产品/杂项/领料），按物料（P0 #6）',
 'admin', NOW(), 'admin', NOW(), b'0', 0,
 '[{"field":"item_code","label":"物料编码"},{"field":"item_name","label":"物料名称"},{"field":"in_quantity","label":"期间入库数量"},{"field":"out_quantity","label":"期间出库数量"},{"field":"stock_quantity","label":"当前在库数量"}]'),

-- ---------------------------------------------------------------------
--    注：各 WM 表建表时期不同、列排序规则不一致，UNION 的字符列统一 CONVERT(... USING utf8mb4)。
--    （11. 出入库明细台账）5 类单据 UNION，含单据状态中文。
-- ---------------------------------------------------------------------
(11, 'mes_wm_inout_ledger', '出入库明细台账', 'WMS',
 'SELECT t.tenant_id AS tenant_id, t.doc_type AS doc_type, t.doc_code AS doc_code, t.doc_date AS doc_date, t.item_code AS item_code, t.item_name AS item_name, t.quantity AS quantity, t.status_name AS status_name FROM ( SELECT ''采购入库'' AS doc_type, CONVERT(r.code USING utf8mb4) AS doc_code, DATE_FORMAT(r.receipt_date, ''%Y-%m-%d'') AS doc_date, CONVERT(i.code USING utf8mb4) AS item_code, CONVERT(i.name USING utf8mb4) AS item_name, l.received_quantity AS quantity, CASE r.status WHEN 0 THEN ''草稿'' WHEN 10 THEN ''BPM审批中'' WHEN 2 THEN ''待上架'' WHEN 3 THEN ''待执行入库'' WHEN 4 THEN ''已完成'' WHEN 5 THEN ''已取消'' ELSE ''其他'' END AS status_name, r.receipt_date AS sort_date, r.tenant_id AS tenant_id FROM mes_wm_item_receipt r LEFT JOIN mes_wm_item_receipt_line l ON l.receipt_id = r.id AND l.deleted = 0 LEFT JOIN mes_md_item i ON i.id = l.item_id WHERE r.deleted = 0 UNION ALL SELECT ''产品入库'' AS doc_type, CONVERT(r.code USING utf8mb4), DATE_FORMAT(r.receipt_date, ''%Y-%m-%d''), CONVERT(i.code USING utf8mb4), CONVERT(i.name USING utf8mb4), l.quantity, CASE r.status WHEN 0 THEN ''草稿'' WHEN 2 THEN ''待上架'' WHEN 3 THEN ''待执行入库'' WHEN 4 THEN ''已完成'' WHEN 5 THEN ''已取消'' ELSE ''其他'' END, r.receipt_date, r.tenant_id FROM mes_wm_product_receipt r LEFT JOIN mes_wm_product_receipt_line l ON l.receipt_id = r.id AND l.deleted = 0 LEFT JOIN mes_md_item i ON i.id = l.item_id WHERE r.deleted = 0 UNION ALL SELECT ''杂项入库'' AS doc_type, CONVERT(r.code USING utf8mb4), DATE_FORMAT(r.receipt_date, ''%Y-%m-%d''), CONVERT(i.code USING utf8mb4), CONVERT(i.name USING utf8mb4), l.quantity, CASE r.status WHEN 0 THEN ''草稿'' WHEN 3 THEN ''已审批'' WHEN 4 THEN ''已完成'' WHEN 5 THEN ''已取消'' ELSE ''其他'' END, r.receipt_date, r.tenant_id FROM mes_wm_misc_receipt r LEFT JOIN mes_wm_misc_receipt_line l ON l.receipt_id = r.id AND l.deleted = 0 LEFT JOIN mes_md_item i ON i.id = l.item_id WHERE r.deleted = 0 UNION ALL SELECT ''领料出库'' AS doc_type, CONVERT(r.code USING utf8mb4), DATE_FORMAT(r.issue_date, ''%Y-%m-%d''), CONVERT(i.code USING utf8mb4), CONVERT(i.name USING utf8mb4), l.quantity, CASE r.status WHEN 0 THEN ''草稿'' WHEN 2 THEN ''待拣货'' WHEN 3 THEN ''待执行领出'' WHEN 4 THEN ''已完成'' WHEN 5 THEN ''已取消'' ELSE ''其他'' END, r.issue_date, r.tenant_id FROM mes_wm_product_issue r LEFT JOIN mes_wm_product_issue_line l ON l.issue_id = r.id AND l.deleted = 0 LEFT JOIN mes_md_item i ON i.id = l.item_id WHERE r.deleted = 0 UNION ALL SELECT ''杂项出库'' AS doc_type, CONVERT(r.code USING utf8mb4), DATE_FORMAT(r.issue_date, ''%Y-%m-%d''), CONVERT(i.code USING utf8mb4), CONVERT(i.name USING utf8mb4), l.quantity, CASE r.status WHEN 0 THEN ''草稿'' WHEN 3 THEN ''待执行出库'' WHEN 4 THEN ''已完成'' WHEN 5 THEN ''已取消'' ELSE ''其他'' END, r.issue_date, r.tenant_id FROM mes_wm_misc_issue r LEFT JOIN mes_wm_misc_issue_line l ON l.issue_id = r.id AND l.deleted = 0 LEFT JOIN mes_md_item i ON i.id = l.item_id WHERE r.deleted = 0 ) t WHERE t.sort_date >= ${startDate} AND t.sort_date < DATE_ADD(${endDate}, INTERVAL 1 DAY) ORDER BY t.sort_date DESC',
 '[{"name":"startDate","label":"开始日期","type":"date","required":false,"defaultValue":"2000-01-01"},{"name":"endDate","label":"结束日期","type":"date","required":false,"defaultValue":"2099-12-31"}]',
 'tenant_id', 5000, 30, 0, '刘洋', '出入库台账：采购/产品/杂项入库 + 领料/杂项出库，含单据状态（P0 #7）',
 'admin', NOW(), 'admin', NOW(), b'0', 0,
 '[{"field":"doc_type","label":"单据类型"},{"field":"doc_code","label":"单据编号"},{"field":"doc_date","label":"单据日期"},{"field":"item_code","label":"物料编码"},{"field":"item_name","label":"物料名称"},{"field":"quantity","label":"数量"},{"field":"status_name","label":"单据状态"}]')

ON DUPLICATE KEY UPDATE `code` = VALUES(`code`), `name` = VALUES(`name`), `category` = VALUES(`category`),
                        `sql_text` = VALUES(`sql_text`), `param_definitions` = VALUES(`param_definitions`),
                        `tenant_column` = VALUES(`tenant_column`), `max_rows` = VALUES(`max_rows`),
                        `timeout_seconds` = VALUES(`timeout_seconds`), `status` = VALUES(`status`),
                        `owner` = VALUES(`owner`), `remark` = VALUES(`remark`), `column_labels` = VALUES(`column_labels`),
                        `deleted` = b'0', `updater` = 'admin', `update_time` = NOW();

-- ---------------------------------------------------------------------
-- 说明：P0 清单 #8 采购订单执行表 / #9 销售发货与回款表属 ERP 域（含金额），
--       按 20260916b 决策「报表体系收敛 MES 域，WMS/ERP 域由对应模块负责人自行登记」，
--       不在 MES 报表中心登记。此前误登记的 id 12/13 在此软删下线；
--       SQL 口径（ErpAuditStatus 中文映射等）已验证可用，ERP 负责人可直接复用：
--         12 采购执行：erp_purchase_order JOIN erp_supplier，in_count/return_count/total_price/执行率
--         13 销售回款：erp_sale_out JOIN erp_customer，total_price/receipt_price/欠款=total-receipt
-- ---------------------------------------------------------------------

-- 软删下线此前误登记的两张 ERP 金额类数据集（幂等）
UPDATE `report_dataset`
SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `id` IN (12, 13)
  AND `code` IN ('erp_purchase_execution', 'erp_sale_shipment_receipt');

COMMIT;
