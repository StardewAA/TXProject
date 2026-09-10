-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 项目域主档（2026-09-10）：项目 / 招标项目 / 标段 三档 + 权限菜单
-- 规范依据（注释四要素之一）：
--   ·《招投标模块开发规范》§5 数据层（bidding_<域>_<实体> 命名、T 轨 TenantBaseDO 租户隔离、唯一键含 deleted、JSON 兜底列）
--   · 公共资源交易平台系统数据规范 V2.0 / 陕西 V2.0 数据集：ProjectVO（项目）、TenderProjectVO（招标项目）、BidSectionVO（标段(包)）
--     —— 权威字段名照录规范（含原文拼写瑕疵），全量落 fields_json，检索/列表所需列单列存储
-- 业务目标：为"一个项目可追溯全部信息"提供主档锚点（公告/外部公告/备案/关注/主体均以
--   project_code / tender_project_code / bid_section_code 关联），补齐此前项目信息仅散落于各业务表字段、无主档的缺口
-- 原因：既有 notice/external/filing/follow 已各自存项目号但无权威主档（项目规模/资金来源/招标人/代理/监督部门等无处落），
--   追溯只能靠 LIKE 拼装且口径不一；建主档后统一锚点，追溯按唯一键精确命中
-- ============================================================================

CREATE TABLE IF NOT EXISTS bidding_project (
  id                    bigint       NOT NULL COMMENT '主键（雪花）',
  tenant_id             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨，拦截器自动维护）',
  project_code          varchar(64)  NOT NULL COMMENT '项目编号（数据集 ProjectVO.PROJECT_CODE，租户内唯一）',
  project_name          varchar(512) NOT NULL COMMENT '项目名称（PROJECT_NAME）',
  region_code           varchar(16)  DEFAULT NULL COMMENT '行政区划代码（REGION_CODE，GB/T 2260）',
  invest_project_code   varchar(64)  DEFAULT NULL COMMENT '投资项目统一代码（INVEST_PROJECT_CODE）',
  relate_code           varchar(64)  DEFAULT NULL COMMENT '关联项目编号（RELATE_CODE）',
  address               varchar(512) DEFAULT NULL COMMENT '建设地点（ADDRESS）',
  legal_person          varchar(128) DEFAULT NULL COMMENT '法定代表人（LEGAL_PERSON）',
  industries_type       varchar(32)  DEFAULT NULL COMMENT '行业类别（INDUSTRIES_TYPE，GB/T 4754）',
  fund_source           varchar(64)  DEFAULT NULL COMMENT '资金来源（FUND_SOURCE）',
  contribution_scale    varchar(64)  DEFAULT NULL COMMENT '出资比例（CONTRIBUTION_SCALE）',
  project_scale         varchar(512) DEFAULT NULL COMMENT '项目规模（PROJECT_SCALE）',
  approval_dept_name    varchar(128) DEFAULT NULL COMMENT '审批部门（APPROVAL_DEPT_NAME）',
  approval_dept_code    varchar(32)  DEFAULT NULL COMMENT '审批部门代码（APPROVAL_DEPT_CODE）',
  approval_doc_no       varchar(128) DEFAULT NULL COMMENT '审批文号（APPROVAL_DOC_NO）',
  state                 varchar(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '内部管理态：ACTIVE=进行中 / CLOSED=已结束（数据集无状态字段，为本模块管理需要自建）',
  fields_json           json         DEFAULT NULL COMMENT 'ProjectVO 数据集全量字段快照（key=规范英文名，缺失补 null）',
  remark                varchar(512) DEFAULT NULL COMMENT '备注',
  creator               varchar(64)  DEFAULT '' COMMENT '创建者',
  create_time           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater               varchar(64)  DEFAULT '' COMMENT '更新者',
  update_time           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_code (tenant_id, project_code, deleted),
  KEY idx_project_name (project_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目主档（数据集 ProjectVO）';

CREATE TABLE IF NOT EXISTS bidding_tender_project (
  id                       bigint       NOT NULL COMMENT '主键（雪花）',
  tenant_id                bigint       NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
  tender_project_code      varchar(64)  NOT NULL COMMENT '招标项目编号（TenderProjectVO.TENDER_PROJECT_CODE，租户内唯一，全链路追溯主键）',
  project_code             varchar(64)  DEFAULT NULL COMMENT '所属项目编号（PROJECT_CODE，关联 bidding_project.project_code）',
  tender_project_name      varchar(512) NOT NULL COMMENT '招标项目名称（TENDER_PROJECT_NAME）',
  tender_project_type      varchar(32)  DEFAULT NULL COMMENT '招标项目类型（TENDER_PROJECT_TYPE）',
  region_code              varchar(16)  DEFAULT NULL COMMENT '行政区划（REGION_CODE）',
  tender_content           text         COMMENT '招标内容（TENDER_CONTENT）',
  tenderer_name            varchar(256) DEFAULT NULL COMMENT '招标人名称（TENDERER_NAME）',
  tenderer_code_type       varchar(8)   DEFAULT NULL COMMENT '招标人代码类型（TENDERER_CODE_TYPE）',
  tenderer_code            varchar(64)  DEFAULT NULL COMMENT '招标人代码（TENDERER_CODE）',
  tender_agency_name       varchar(256) DEFAULT NULL COMMENT '招标代理机构名称（TENDER_AGENCY_NAME）',
  tender_agency_code_type  varchar(8)   DEFAULT NULL COMMENT '代理机构代码类型（TENDER_AGENCY_CODE_TYPE）',
  tender_agency_code       varchar(64)  DEFAULT NULL COMMENT '代理机构代码（TENDER_AGENCY_CODE）',
  tender_mode              varchar(16)  DEFAULT NULL COMMENT '招标方式（TENDER_MODE）',
  qulification_check_mode  varchar(16)  DEFAULT NULL COMMENT '资格审查方式（QULIFICATION_CHECK_MODE，规范原文拼写照录）',
  tender_organize_form     varchar(16)  DEFAULT NULL COMMENT '招标组织形式（TENDER_ORGANIZE_FORM）',
  supervise_dept_name      varchar(256) DEFAULT NULL COMMENT '监督部门名称（SUPERVISE_DEPT_NAME）',
  supervise_dept_code      varchar(32)  DEFAULT NULL COMMENT '监督部门代码（SUPERVISE_DEPT_CODE）',
  approve_dept_name        varchar(256) DEFAULT NULL COMMENT '审批部门名称（APPROVE_DEPT_NAME）',
  approve_dept_code        varchar(32)  DEFAULT NULL COMMENT '审批部门代码（APPROVE_DEPT_CODE）',
  state                    varchar(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '内部管理态：ACTIVE/CLOSED（同项目主档口径）',
  fields_json              json         DEFAULT NULL COMMENT 'TenderProjectVO 全量字段快照',
  remark                   varchar(512) DEFAULT NULL COMMENT '备注',
  creator                  varchar(64)  DEFAULT '' COMMENT '创建者',
  create_time              datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater                  varchar(64)  DEFAULT '' COMMENT '更新者',
  update_time              datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted                  bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tender_project_code (tenant_id, tender_project_code, deleted),
  KEY idx_tp_project (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='招标项目主档（数据集 TenderProjectVO）';

CREATE TABLE IF NOT EXISTS bidding_bid_section (
  id                    bigint       NOT NULL COMMENT '主键（雪花）',
  tenant_id             bigint       NOT NULL DEFAULT 0 COMMENT '租户编号（T 轨）',
  bid_section_code      varchar(64)  NOT NULL COMMENT '标段（包）编号（BidSectionVO.BID_SECTION_CODE，租户内唯一）',
  tender_project_code   varchar(64)  NOT NULL COMMENT '所属招标项目编号（TENDER_PROJECT_CODE）',
  bid_section_name      varchar(512) NOT NULL COMMENT '标段（包）名称（BID_SECTION_NAME）',
  unified_deal_code     varchar(64)  DEFAULT NULL COMMENT '统一交易标识码（UNIFIED_DEAL_CODE）',
  bid_ection_num        varchar(32)  DEFAULT NULL COMMENT '标段（包）序号（BID_ECTION_NUM，规范原文拼写照录）',
  bid_tender_mode       varchar(16)  DEFAULT NULL COMMENT '招标方式（BID_TENDER_MODE）',
  region_code           varchar(16)  DEFAULT NULL COMMENT '行政区划（REGION_CODE）',
  tra_place_code        varchar(32)  DEFAULT NULL COMMENT '交易场地代码（TRA_PLACE_CODE）',
  bid_section_content   text         COMMENT '标段（包）内容（BID_SECTION_CONTENT）',
  bid_section_type      varchar(32)  DEFAULT NULL COMMENT '标段（包）类型（BID_SECTION_TYPE）',
  contract_reckon_price varchar(32)  DEFAULT NULL COMMENT '合同估算价（CONTRACT_RECKON_PRICE，按数据集保真为字符串）',
  currency_code         varchar(8)   DEFAULT NULL COMMENT '币种（CURRENCY_CODE）',
  price_unit            varchar(16)  DEFAULT NULL COMMENT '价格单位（PRICE_UNIT）',
  time_limit            varchar(64)  DEFAULT NULL COMMENT '工期/服务期（TIME_LIMIT）',
  bid_qualification     text         COMMENT '投标人资格条件（BID_QUALIFICATION）',
  is_web_bidding        varchar(8)   DEFAULT NULL COMMENT '是否电子招投标（IS_WEB_BIDDING）',
  is_nomeet_bidding     varchar(8)   DEFAULT NULL COMMENT '是否不见面开标（IS_NOMEET_BIDDING）',
  state                 varchar(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '内部管理态：ACTIVE/CLOSED',
  fields_json           json         DEFAULT NULL COMMENT 'BidSectionVO 全量字段快照',
  remark                varchar(512) DEFAULT NULL COMMENT '备注',
  creator               varchar(64)  DEFAULT '' COMMENT '创建者',
  create_time           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater               varchar(64)  DEFAULT '' COMMENT '更新者',
  update_time           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted               bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_bid_section_code (tenant_id, bid_section_code, deleted),
  KEY idx_bs_tender_project (tender_project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标段（包）主档（数据集 BidSectionVO）';

-- ========== 菜单与权限点（system_menu，id 6770+ 段；不与 certificate/notice 段冲突） ==========
-- 目录 6770「项目与标段」（挂招投标目录 6735 下）；页面 6771 项目台账；页面 6772 标段台账
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6770, '项目与标段', '', 1, 30, 6735, 'project', 'ep:collection', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6771, '项目台账', 'bidding:project:query', 2, 1, 6770, 'project', 'ep:document', 'bidding/project/index', 'BiddingProject', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6772, '标段台账', 'bidding:bid-section:query', 2, 2, 6770, 'bid-section', 'ep:grid', 'bidding/bidSection/index', 'BiddingBidSection', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6773, '项目新建', 'bidding:project:create', 3, 1, 6771, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6774, '项目修改', 'bidding:project:update', 3, 2, 6771, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6775, '标段新建', 'bidding:bid-section:create', 3, 1, 6772, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');
INSERT IGNORE INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES (6776, '标段修改', 'bidding:bid-section:update', 3, 2, 6772, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 角色授权：平台超管/普通角色（1/2）+ 租户业务角色（60346 甲方超级管理员、60352 招标管理员）——租户侧项目/标段属业务功能（与 notice 域口径一致）
INSERT IGNORE INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT r.id, m.id, '1', NOW(), '1', NOW(), b'0', 0
FROM system_role r JOIN system_menu m ON m.id BETWEEN 6770 AND 6776
WHERE r.id IN (1, 2);

SELECT COUNT(*) AS new_menus FROM system_menu WHERE id BETWEEN 6770 AND 6776;
