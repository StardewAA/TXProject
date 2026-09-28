-- ============================================================
-- 内容管理运行时修复
-- 只补充缺失结构，不删除、不清空现有业务表或数据。
-- ============================================================

CREATE TABLE IF NOT EXISTS cms_policy (
  policy_id bigint NOT NULL AUTO_INCREMENT COMMENT '政策ID',
  content_type varchar(16) NOT NULL DEFAULT '金融政策' COMMENT '内容类型：法律法规/金融政策',
  title varchar(256) NOT NULL COMMENT '政策标题',
  policy_level tinyint DEFAULT NULL COMMENT '层级：1国家 2省 3市 4区县',
  publish_dept varchar(128) DEFAULT NULL COMMENT '发布部门',
  category varchar(64) DEFAULT NULL COMMENT '主题分类',
  effective_status varchar(20) NOT NULL DEFAULT '现行有效' COMMENT '效力状态',
  publish_date date DEFAULT NULL COMMENT '发布日期',
  effective_date date DEFAULT NULL COMMENT '生效日期',
  implement_date date DEFAULT NULL COMMENT '实施日期',
  content longtext COMMENT '政策正文内容',
  summary varchar(500) DEFAULT NULL COMMENT '内容摘要',
  source_url varchar(512) DEFAULT NULL COMMENT '原文链接',
  source_name varchar(128) DEFAULT NULL COMMENT '来源网站名称',
  attachment_urls json DEFAULT NULL COMMENT '附件URL列表',
  view_count int NOT NULL DEFAULT 0 COMMENT '浏览量',
  download_count int NOT NULL DEFAULT 0 COMMENT '下载量',
  is_top tinyint NOT NULL DEFAULT 0 COMMENT '是否置顶：0否 1是',
  audit_status tinyint NOT NULL DEFAULT 3 COMMENT '审核：0待审 1通过 2驳回 3草稿',
  audit_remark varchar(512) DEFAULT NULL COMMENT '审核备注',
  publish_status tinyint NOT NULL DEFAULT 0 COMMENT '发布：0未发布 1已发布 2已下架',
  publish_time datetime DEFAULT NULL COMMENT '发布时间',
  create_by bigint DEFAULT NULL COMMENT '创建人ID',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人ID',
  update_time datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (policy_id),
  KEY idx_level (policy_level),
  KEY idx_publish_date (publish_date),
  KEY idx_publish (publish_status,audit_status),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='政策资讯';

-- 兼容已经创建过、但缺少推荐图片字段的旧表。
SET @has_image_url = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'cms_recommend_slot'
    AND COLUMN_NAME = 'image_url'
);
SET @add_image_url_sql = IF(
  @has_image_url = 0,
  'ALTER TABLE cms_recommend_slot ADD COLUMN image_url varchar(1024) NULL COMMENT ''推荐图片地址'' AFTER title',
  'SELECT 1'
);
PREPARE add_image_url_stmt FROM @add_image_url_sql;
EXECUTE add_image_url_stmt;
DEALLOCATE PREPARE add_image_url_stmt;
