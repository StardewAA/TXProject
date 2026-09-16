-- =====================================================================
-- 2026-09-16（追加）报表数据集收敛为「MES 数据集」
-- 背景：报表体系归 MES 域（服务车间/品质/生产文员：生产日报、工单完工、
--       不良品统计、物料耗用、设备效率），因此：
--         1. 数据集只登记 MES 域，非 MES 域的种子数据集下线（软删，可恢复）；
--         2. 数据集管理菜单从「报表管理」迁到「MES 系统」下；
--         3. 补齐 5 个 MES 业务报表演所需的数据集。
-- 说明：幂等，可重复执行。
-- =====================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- 1. 非 MES 域的数据集下线（软删；WMS/ERP 域数据集由对应模块负责人自行登记）
-- ---------------------------------------------------------------------
UPDATE `report_dataset`
SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `code` IN ('mes_stock_summary')
  AND `deleted` = b'0';

-- ---------------------------------------------------------------------
-- 2. 菜单迁移：数据集管理 6800 从「报表管理」(1281) 迁到「MES 系统」(5100)
-- ---------------------------------------------------------------------
UPDATE `system_menu`
SET `parent_id` = 5100,
    `name`      = '报表数据集',
    `path`      = 'report-dataset',
    `sort`      = 90,
    `updater`   = 'admin',
    `update_time` = NOW(),
    `deleted`   = b'0'
WHERE `id` = 6800;

-- 权限按钮同步挂到新的父菜单下
UPDATE `system_menu`
SET `parent_id` = 6800, `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` BETWEEN 6801 AND 6805;

-- ---------------------------------------------------------------------
-- 3. MES 业务报表数据集（平台级：tenant_id = 0，所有租户只读可见）
--    约定：SQL 必须把 tenant_id 列进 SELECT，租户过滤由服务端强制追加
-- ---------------------------------------------------------------------
INSERT INTO `report_dataset`
(`id`, `code`, `name`, `category`, `sql_text`, `param_definitions`, `tenant_column`,
 `max_rows`, `timeout_seconds`, `status`, `owner`, `remark`, `creator`, `create_time`,
 `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
-- 3. 生产日报
(3, 'mes_daily_production', '生产日报（按日期/工单/工序）', 'MES',
 'SELECT f.tenant_id AS tenant_id, DATE(f.feedback_time) AS report_date, w.code AS work_order_code, p.name AS process_name, COUNT(*) AS record_count, SUM(f.feedback_quantity) AS feedback_quantity, SUM(f.qualified_quantity) AS qualified_quantity, SUM(f.unqualified_quantity) AS unqualified_quantity FROM mes_pro_feedback f LEFT JOIN mes_pro_work_order w ON w.id = f.work_order_id LEFT JOIN mes_pro_process p ON p.id = f.process_id WHERE f.deleted = 0 AND f.status IN (2, 3, 4) AND f.feedback_time >= ${startDate} AND f.feedback_time < DATE_ADD(${endDate}, INTERVAL 1 DAY) GROUP BY f.tenant_id, DATE(f.feedback_time), w.code, p.name ORDER BY report_date DESC',
 '[{"name":"startDate","label":"开始日期","type":"date","required":false,"defaultValue":"2000-01-01"},{"name":"endDate","label":"结束日期","type":"date","required":false,"defaultValue":"2099-12-31"}]',
 'tenant_id', 5000, 30, 0, '刘洋', '生产日报：按日期/工单/工序汇总报工与合格数量', 'admin', NOW(), 'admin', NOW(), b'0', 0),

-- 4. 工单完工报表
(4, 'mes_work_order_completion', '工单完工报表', 'MES',
 'SELECT w.tenant_id AS tenant_id, w.code AS work_order_code, w.name AS work_order_name, i.code AS item_code, i.name AS item_name, w.quantity AS plan_quantity, w.quantity_produced AS produced_quantity, ROUND(CASE WHEN w.quantity > 0 THEN w.quantity_produced / w.quantity * 100 ELSE 0 END, 2) AS progress_percent, w.status AS status, w.request_date AS request_date, w.finish_date AS finish_date FROM mes_pro_work_order w LEFT JOIN mes_md_item i ON i.id = w.product_id WHERE w.deleted = 0 AND w.request_date >= ${startDate} AND w.request_date < DATE_ADD(${endDate}, INTERVAL 1 DAY) ORDER BY w.request_date DESC',
 '[{"name":"startDate","label":"计划开始日期","type":"date","required":false,"defaultValue":"2000-01-01"},{"name":"endDate","label":"计划结束日期","type":"date","required":false,"defaultValue":"2099-12-31"}]',
 'tenant_id', 5000, 30, 0, '刘洋', '工单完工报表：计划/已产/进度/状态/完工日期', 'admin', NOW(), 'admin', NOW(), b'0', 0),

-- 5. 不良品统计
(5, 'mes_defect_summary', '不良品统计（按物料/工序）', 'MES',
 'SELECT f.tenant_id AS tenant_id, i.code AS item_code, i.name AS item_name, p.name AS process_name, SUM(f.unqualified_quantity) AS unqualified_quantity, SUM(f.labor_scrap_quantity) AS labor_scrap_quantity, SUM(f.material_scrap_quantity) AS material_scrap_quantity, SUM(f.other_scrap_quantity) AS other_scrap_quantity FROM mes_pro_feedback f LEFT JOIN mes_md_item i ON i.id = f.item_id LEFT JOIN mes_pro_process p ON p.id = f.process_id WHERE f.deleted = 0 AND f.status IN (2, 3, 4) AND f.feedback_time >= ${startDate} AND f.feedback_time < DATE_ADD(${endDate}, INTERVAL 1 DAY) GROUP BY f.tenant_id, i.code, i.name, p.name ORDER BY unqualified_quantity DESC',
 '[{"name":"startDate","label":"开始日期","type":"date","required":false,"defaultValue":"2000-01-01"},{"name":"endDate","label":"结束日期","type":"date","required":false,"defaultValue":"2099-12-31"}]',
 'tenant_id', 5000, 30, 0, '刘洋', '不良品统计：不良/工废/料废按物料与工序汇总', 'admin', NOW(), 'admin', NOW(), b'0', 0),

-- 6. 物料耗用报表
(6, 'mes_material_consumption', '物料耗用报表（按工单/物料）', 'MES',
 'SELECT c.tenant_id AS tenant_id, w.code AS work_order_code, p.name AS process_name, DATE(c.consume_date) AS consume_date, i.code AS item_code, i.name AS item_name, SUM(d.quantity) AS consume_quantity FROM mes_wm_item_consume_detail d JOIN mes_wm_item_consume c ON c.id = d.consume_id LEFT JOIN mes_pro_work_order w ON w.id = c.work_order_id LEFT JOIN mes_pro_process p ON p.id = c.process_id LEFT JOIN mes_md_item i ON i.id = d.item_id WHERE d.deleted = 0 AND c.deleted = 0 AND c.consume_date >= ${startDate} AND c.consume_date < DATE_ADD(${endDate}, INTERVAL 1 DAY) GROUP BY c.tenant_id, w.code, p.name, DATE(c.consume_date), i.code, i.name ORDER BY consume_date DESC',
 '[{"name":"startDate","label":"开始日期","type":"date","required":false,"defaultValue":"2000-01-01"},{"name":"endDate","label":"结束日期","type":"date","required":false,"defaultValue":"2099-12-31"}]',
 'tenant_id', 5000, 30, 0, '刘洋', '物料耗用：生产领料消耗按工单/物料汇总', 'admin', NOW(), 'admin', NOW(), b'0', 0),

-- 7. 设备运行与维修统计（OEE 的运行/维修侧数据；完整 OEE 需接入设备运行时长采集）
(7, 'mes_machinery_efficiency', '设备运行与维修统计', 'MES',
 'SELECT m.tenant_id AS tenant_id, m.code AS machinery_code, m.name AS machinery_name, m.status AS status, COUNT(r.id) AS repair_count, SUM(CASE WHEN r.finish_date IS NOT NULL AND r.require_date IS NOT NULL THEN TIMESTAMPDIFF(HOUR, r.require_date, r.finish_date) ELSE 0 END) AS repair_hours, m.last_check_time AS last_check_time, m.last_mainten_time AS last_mainten_time FROM mes_dv_machinery m LEFT JOIN mes_dv_repair r ON r.machinery_id = m.id AND r.deleted = 0 WHERE m.deleted = 0 GROUP BY m.tenant_id, m.code, m.name, m.status, m.last_check_time, m.last_mainten_time ORDER BY repair_hours DESC',
 '[]',
 'tenant_id', 5000, 30, 0, '刘洋', '设备运行与维修统计；完整 OEE 待接入设备运行时长后补齐', 'admin', NOW(), 'admin', NOW(), b'0', 0)
ON DUPLICATE KEY UPDATE `code` = VALUES(`code`), `name` = VALUES(`name`), `category` = VALUES(`category`),
                        `sql_text` = VALUES(`sql_text`),
                        `param_definitions` = VALUES(`param_definitions`), `tenant_column` = VALUES(`tenant_column`),
                        `max_rows` = VALUES(`max_rows`), `timeout_seconds` = VALUES(`timeout_seconds`),
                        `status` = VALUES(`status`), `owner` = VALUES(`owner`),
                        `remark` = VALUES(`remark`), `deleted` = b'0',
                        `updater` = 'admin', `update_time` = NOW();

COMMIT;
