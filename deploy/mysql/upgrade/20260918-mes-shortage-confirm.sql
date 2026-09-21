-- MES 收件箱增加协作单 G 确认状态（设计 6.3.5/6.3.6）
--
-- MES 收到 WMS 协作单 G 后，先进入待确认；MES 确认/驳回后更新该状态，
-- 确认时再创建正式生产工单并回填工单号，驳回时记录原因。

SET @ddl := (
    SELECT CONCAT('ALTER TABLE `mes_wms_shortage_inbox` ',
                  GROUP_CONCAT(CONCAT('ADD COLUMN ', column_def) SEPARATOR ', '))
    FROM (SELECT '`confirm_status` varchar(32) NOT NULL DEFAULT ''AWAITING_CONFIRM'' COMMENT ''MES 确认状态：AWAITING_CONFIRM 待确认 / CONFIRMED 已确认 / REJECTED 已驳回''' AS column_def, 'confirm_status' AS column_name
          UNION ALL SELECT '`mes_work_order_no` varchar(64) NULL COMMENT ''MES 确认后生成的生产工单号''', 'mes_work_order_no'
          UNION ALL SELECT '`reject_reason` varchar(512) NULL COMMENT ''MES 驳回原因''', 'reject_reason') defs
    WHERE NOT EXISTS (SELECT 1
                      FROM information_schema.COLUMNS c
                      WHERE c.TABLE_SCHEMA = DATABASE()
                        AND c.TABLE_NAME = 'mes_wms_shortage_inbox'
                        AND c.COLUMN_NAME = defs.column_name)
);
SET @ddl := IFNULL(@ddl, 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
