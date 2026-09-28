-- 专家咨询管理加固：退款重试、订单事件、完成后只读、服务排班与资质字段。
-- 可重复执行。

DROP PROCEDURE IF EXISTS txgy_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE txgy_add_column_if_missing(
  IN p_table_name VARCHAR(64),
  IN p_column_name VARCHAR(64),
  IN p_column_ddl VARCHAR(2000)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_ddl);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL txgy_add_column_if_missing('consult_order', 'service_id',
  'service_id bigint NULL COMMENT ''专家服务配置编号'' AFTER expert_id');
CALL txgy_add_column_if_missing('consult_order', 'service_type',
  'service_type varchar(32) NOT NULL DEFAULT ''TEXT'' COMMENT ''咨询类型快照'' AFTER service_id');
CALL txgy_add_column_if_missing('consult_order', 'duration_minutes',
  'duration_minutes int NOT NULL DEFAULT 60 COMMENT ''咨询时长快照（分钟）'' AFTER service_type');
CALL txgy_add_column_if_missing('consult_order', 'appointment_end_time',
  'appointment_end_time datetime NULL COMMENT ''预约结束时间'' AFTER appointment_time');
CALL txgy_add_column_if_missing('consult_order', 'refund_order_id',
  'refund_order_id bigint NULL COMMENT ''支付模块退款单编号'' AFTER pay_order_id');
CALL txgy_add_column_if_missing('consult_order', 'refund_requested_time',
  'refund_requested_time datetime NULL COMMENT ''首次发起退款时间'' AFTER refund_order_id');
CALL txgy_add_column_if_missing('consult_order', 'refund_attempt_count',
  'refund_attempt_count int NOT NULL DEFAULT 0 COMMENT ''退款请求尝试次数'' AFTER refund_requested_time');
CALL txgy_add_column_if_missing('consult_order', 'refund_next_retry_time',
  'refund_next_retry_time datetime NULL COMMENT ''下次退款重试时间'' AFTER refund_attempt_count');
CALL txgy_add_column_if_missing('consult_order', 'refund_last_error',
  'refund_last_error varchar(500) NULL COMMENT ''最近一次退款请求错误'' AFTER refund_next_retry_time');
CALL txgy_add_column_if_missing('consult_order', 'im_read_only_time',
  'im_read_only_time datetime NULL COMMENT ''咨询群转为只读的时间'' AFTER im_group_id');

CALL txgy_add_column_if_missing('cms_expert_profile', 'id_card_masked',
  'id_card_masked varchar(32) NULL COMMENT ''脱敏身份证号'' AFTER phone');
CALL txgy_add_column_if_missing('cms_expert_profile', 'id_card_hash',
  'id_card_hash char(64) NULL COMMENT ''身份证号单向哈希'' AFTER id_card_masked');
CALL txgy_add_column_if_missing('cms_expert_profile', 'real_name_provider',
  'real_name_provider varchar(32) NULL COMMENT ''实名核验渠道'' AFTER verify_result');
CALL txgy_add_column_if_missing('cms_expert_profile', 'real_name_time',
  'real_name_time datetime NULL COMMENT ''实名核验时间'' AFTER real_name_provider');
CALL txgy_add_column_if_missing('cms_expert_profile', 'qualification_files',
  'qualification_files json NULL COMMENT ''资质附件数组'' AFTER real_name_time');
CALL txgy_add_column_if_missing('cms_expert_profile', 'qualification_review_remark',
  'qualification_review_remark varchar(500) NULL COMMENT ''资质审核意见'' AFTER qualification_files');

CALL txgy_add_column_if_missing('im_group', 'consult_read_only',
  'consult_read_only bit(1) NOT NULL DEFAULT b''0'' COMMENT ''咨询订单结束后整群只读'' AFTER muted_all');

DROP PROCEDURE IF EXISTS txgy_add_column_if_missing;

CREATE TABLE IF NOT EXISTS consult_order_event (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  order_id bigint NOT NULL,
  event_type varchar(32) NOT NULL,
  from_status tinyint NULL,
  to_status tinyint NULL,
  operator_id bigint NULL,
  operator_type varchar(16) NOT NULL DEFAULT 'SYSTEM',
  remark varchar(500) NULL,
  snapshot json NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_consult_order_event_order (tenant_id, order_id, id),
  KEY idx_consult_order_event_type (tenant_id, event_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家咨询订单事件与状态历史';

CREATE TABLE IF NOT EXISTS cms_expert_service (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  expert_id bigint NOT NULL,
  title varchar(100) NOT NULL,
  service_type varchar(32) NOT NULL COMMENT 'TEXT/AUDIO/VIDEO/DOCUMENT',
  duration_minutes int NOT NULL,
  price int NOT NULL COMMENT '价格，分',
  description varchar(1000) NULL,
  enabled bit(1) NOT NULL DEFAULT b'1',
  sort int NOT NULL DEFAULT 0,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  version int NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cms_expert_service (tenant_id, expert_id, service_type, duration_minutes, deleted),
  KEY idx_cms_expert_service_expert (tenant_id, expert_id, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家咨询服务与价格配置';

CREATE TABLE IF NOT EXISTS cms_expert_schedule (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  expert_id bigint NOT NULL,
  weekday tinyint NOT NULL COMMENT '1=周一，7=周日',
  start_time time NOT NULL,
  end_time time NOT NULL,
  timezone varchar(64) NOT NULL DEFAULT 'Asia/Shanghai',
  enabled bit(1) NOT NULL DEFAULT b'1',
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  version int NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cms_expert_schedule (tenant_id, expert_id, weekday, start_time, end_time, deleted),
  KEY idx_cms_expert_schedule_expert (tenant_id, expert_id, weekday, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家每周可预约时段';

CREATE TABLE IF NOT EXISTS cms_expert_schedule_exception (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  expert_id bigint NOT NULL,
  start_time datetime NOT NULL,
  end_time datetime NOT NULL,
  exception_type varchar(16) NOT NULL COMMENT 'BLOCK/OPEN',
  reason varchar(500) NULL,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  version int NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_cms_expert_schedule_exception (tenant_id, expert_id, start_time, end_time, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家排班例外时间';