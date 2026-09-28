-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 招标全流程状态流转（2026-09-21）：流转留痕表 + 流转权限菜单
-- 规范依据：docs/前端规范-全域接口说明/26-招标全流程状态机设计 §5（事件驱动、流转必须留痕：操作人/时间/前后状态/IP）
-- 对应端点：POST /bidding/flow/transition（bidding:flow:transition）、GET /bidding/flow/next-actions、GET /bidding/flow/logs（bidding:flow:query）
-- ============================================================================

-- 流转留痕表（G 轨：业务编号全局定位；审计只追加；带 deleted 兼容 BaseDO 逻辑删除）
CREATE TABLE IF NOT EXISTS bidding_flow_log (
  id          bigint      NOT NULL COMMENT '主键（雪花）',
  biz_type    varchar(32) NOT NULL COMMENT '实体类型：PROJECT/TENDER_PROJECT/BID_SECTION',
  biz_code    varchar(64) NOT NULL COMMENT '业务编号（project_code/tender_project_code/bid_section_code）',
  tenant_id   bigint      NOT NULL DEFAULT 0 COMMENT '租户编号（租户插件自动注入；同业务编号租户内唯一）',
  from_state  varchar(32) DEFAULT NULL COMMENT '流转前状态（状态枚举 code）',
  to_state    varchar(32) NOT NULL COMMENT '流转后状态（状态枚举 code）',
  event       varchar(64) DEFAULT NULL COMMENT '流转事件（FlowTransitionEventEnum code）',
  operator    varchar(64) DEFAULT '' COMMENT '操作人',
  operator_ip varchar(64) DEFAULT '' COMMENT '操作 IP',
  remark      varchar(512) DEFAULT NULL COMMENT '流转说明/审批意见',
  creator     varchar(64) DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater     varchar(64) DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     bit(1)     NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  KEY idx_biz_code (biz_type, biz_code, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招标全流程状态流转留痕（G轨）';

-- 流转权限菜单（parent=6800 招标全流程目录；6802/6803 空闲）
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6802, '流转执行', 'bidding:flow:transition', 3, 2, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6803, '流转查询', 'bidding:flow:query', 3, 3, 6800, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
