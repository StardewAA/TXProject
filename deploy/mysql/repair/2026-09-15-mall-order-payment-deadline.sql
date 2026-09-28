-- 商城支付期限：在目标商城数据库备份后执行，必须先于新版 trade-server 部署。
-- 只增加 nullable 字段和索引，不改历史订单/账号/租户，不操作支付、ERP 或 WMS 表。
-- 支持重复执行；MySQL DDL 自动提交，失败后修正原因并重跑，不能依赖事务回滚 DDL。
-- 回退应用时保留新增列和快照，禁止删列或用当前配置批量回填老单。
SET @mall_deadline_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'pay_expire_time') = 0,
  'ALTER TABLE trade_order ADD COLUMN pay_expire_time DATETIME NULL COMMENT ''首次可付款时确定的支付截止时间''',
  'SELECT ''pay_expire_time already exists''');
PREPARE mall_deadline_stmt FROM @mall_deadline_ddl;
EXECUTE mall_deadline_stmt;
DEALLOCATE PREPARE mall_deadline_stmt;

SET @mall_deadline_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'erp_wait_expire_time') = 0,
  'ALTER TABLE trade_order ADD COLUMN erp_wait_expire_time DATETIME NULL COMMENT ''订单等待 ERP 预占确认的截止时间''',
  'SELECT ''erp_wait_expire_time already exists''');
PREPARE mall_deadline_stmt FROM @mall_deadline_ddl;
EXECUTE mall_deadline_stmt;
DEALLOCATE PREPARE mall_deadline_stmt;

SET @mall_deadline_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'pay_expire_seconds') = 0,
  'ALTER TABLE trade_order ADD COLUMN pay_expire_seconds BIGINT NULL COMMENT ''下单时支付时限秒数快照，NULL为旧规则订单''',
  'SELECT ''pay_expire_seconds already exists''');
PREPARE mall_deadline_stmt FROM @mall_deadline_ddl;
EXECUTE mall_deadline_stmt;
DEALLOCATE PREPARE mall_deadline_stmt;

SET @mall_deadline_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'trade_order' AND INDEX_NAME = 'idx_mall_pay_deadline') = 0,
  'CREATE INDEX idx_mall_pay_deadline ON trade_order (tenant_id, status, pay_expire_time)',
  'SELECT ''idx_mall_pay_deadline already exists''');
PREPARE mall_deadline_stmt FROM @mall_deadline_ddl;
EXECUTE mall_deadline_stmt;
DEALLOCATE PREPARE mall_deadline_stmt;

SET @mall_deadline_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'trade_order' AND INDEX_NAME = 'idx_mall_erp_wait_deadline') = 0,
  'CREATE INDEX idx_mall_erp_wait_deadline ON trade_order (tenant_id, status, erp_wait_expire_time)',
  'SELECT ''idx_mall_erp_wait_deadline already exists''');
PREPARE mall_deadline_stmt FROM @mall_deadline_ddl;
EXECUTE mall_deadline_stmt;
DEALLOCATE PREPARE mall_deadline_stmt;
