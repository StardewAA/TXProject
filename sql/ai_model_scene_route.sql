-- AI 模型按业务场景热路由。执行前请备份数据库。
CREATE TABLE IF NOT EXISTS ai_model_scene_route (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  scene_code varchar(64) NOT NULL COMMENT 'FINANCE_INTERPRETATION/FINANCE_EMBEDDING/CMS_REVIEW',
  primary_model_id bigint NOT NULL,
  fallback_model_id bigint NULL,
  enabled tinyint NOT NULL DEFAULT 1,
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_scene_route (tenant_id, scene_code, deleted),
  KEY idx_ai_scene_primary (primary_model_id),
  KEY idx_ai_scene_fallback (fallback_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型业务场景主备路由';

-- 示例：将模型编号替换为“AI 大模型 -> 模型管理”中的真实编号后执行。
-- INSERT INTO ai_model_scene_route(tenant_id,scene_code,primary_model_id,fallback_model_id)
-- VALUES (1,'FINANCE_INTERPRETATION',1001,1002),
--        (1,'FINANCE_EMBEDDING',2001,2002),
--        (1,'CMS_REVIEW',1002,1001);
