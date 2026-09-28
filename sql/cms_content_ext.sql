-- ============================================================
-- 内容管理能力补充：栏目树（含栏目级权限）、素材库、推荐位、SEO、审查任务
-- 幂等：可重复执行
-- ============================================================

-- 栏目树（多级/排序/显隐/栏目级权限）
CREATE TABLE IF NOT EXISTS cms_category (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  parent_id bigint NOT NULL DEFAULT 0,
  name varchar(64) NOT NULL COMMENT '栏目名称',
  code varchar(64) NOT NULL COMMENT '栏目编码',
  content_type varchar(32) NULL COMMENT '绑定内容类型：STANDARD/MATERIAL/PROCESS/QUOTA/POLICY/ARTICLE',
  sort int NOT NULL DEFAULT 0 COMMENT '排序',
  visible bit(1) NOT NULL DEFAULT b'1' COMMENT '是否显示',
  status tinyint NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  visible_role_codes varchar(512) NULL COMMENT '可见角色编码，逗号分隔，空=全部可见（栏目级权限）',
  remark varchar(255) NULL,
  creator varchar(64) NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cms_category_code (tenant_id, code, deleted),
  KEY idx_cms_category_parent (tenant_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS栏目树';

-- 素材库（图片/PDF/视频）
CREATE TABLE IF NOT EXISTS cms_asset (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  name varchar(255) NOT NULL COMMENT '素材名称',
  asset_type varchar(16) NOT NULL COMMENT 'IMAGE/PDF/VIDEO/FILE',
  url varchar(1024) NOT NULL COMMENT '访问地址',
  size bigint NULL COMMENT '字节',
  mime varchar(128) NULL,
  category_id bigint NULL COMMENT '所属栏目',
  tags varchar(255) NULL,
  usage_count int NOT NULL DEFAULT 0 COMMENT '引用次数',
  creator varchar(64) NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_cms_asset_type (tenant_id, asset_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS素材库';

-- 推荐位
CREATE TABLE IF NOT EXISTS cms_recommend_slot (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  name varchar(128) NOT NULL COMMENT '推荐位名称',
  code varchar(64) NOT NULL COMMENT '推荐位编码',
  position varchar(64) NOT NULL COMMENT '展示位置：HOME_BANNER/PORTAL_SIDEBAR/PORTAL_LIST',
  content_type varchar(32) NULL COMMENT '关联内容类型',
  content_id bigint NULL COMMENT '关联内容ID',
  title varchar(255) NULL,
  image_url varchar(1024) NULL COMMENT '推荐图片地址',
  target_url varchar(512) NULL,
  sort int NOT NULL DEFAULT 0,
  start_time datetime NULL,
  end_time datetime NULL,
  status tinyint NOT NULL DEFAULT 0 COMMENT '0上线 1下线',
  remark varchar(255) NULL,
  creator varchar(64) NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_cms_slot_position (tenant_id, position, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS推荐位';

-- SEO 元信息
CREATE TABLE IF NOT EXISTS cms_seo_meta (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  content_type varchar(32) NOT NULL,
  content_id bigint NOT NULL,
  seo_title varchar(255) NULL,
  seo_keywords varchar(512) NULL,
  seo_description varchar(1024) NULL,
  canonical_url varchar(512) NULL,
  creator varchar(64) NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cms_seo_meta (tenant_id, content_type, content_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS SEO元信息';

-- 审查任务（分配 + 超时提醒）
CREATE TABLE IF NOT EXISTS cms_review_task (
  id bigint NOT NULL AUTO_INCREMENT,
  tenant_id bigint NOT NULL,
  review_id varchar(64) NOT NULL COMMENT '关联 cms_document_review.review_id',
  title varchar(255) NULL,
  assignee_id bigint NULL COMMENT '审查人用户ID',
  assignee_name varchar(64) NULL,
  status varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待处理/PROCESSING处理中/DONE已完成/OVERDUE已超时',
  priority tinyint NOT NULL DEFAULT 1 COMMENT '1普通 2紧急',
  due_time datetime NULL COMMENT '要求完成时间',
  finished_time datetime NULL,
  remark varchar(500) NULL,
  creator varchar(64) NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cms_review_task (tenant_id, review_id, deleted),
  KEY idx_cms_review_task_assignee (tenant_id, assignee_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS审查任务';

-- ============ 菜单 ============
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT 70046,'栏目树','cms:content:query',2,5,70040,'category','ep:menu','cms/content/category/index','CmsContentCategory',0,b'1',b'1',b'1','1','1'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_menu WHERE id=70046) t);

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT 70047,'素材库','cms:content:query',2,60,70040,'asset','ep:picture','cms/content/asset/index','CmsContentAsset',0,b'1',b'1',b'1','1','1'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_menu WHERE id=70047) t);

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT 70048,'推荐位','cms:content:query',2,70,70040,'recommend','ep:star','cms/content/recommend/index','CmsContentRecommend',0,b'1',b'1',b'1','1','1'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_menu WHERE id=70048) t);

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT 70049,'SEO 设置','cms:content:query',2,80,70040,'seo','ep:search','cms/content/seo/index','CmsContentSeo',0,b'1',b'1',b'1','1','1'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_menu WHERE id=70049) t);

INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater)
SELECT 70050,'审查任务分配','cms:review:query',2,5,70007,'task','ep:alarm-clock','cms/review/task/index','CmsReviewTask',1,b'0',b'1',b'1','1','1'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_menu WHERE id=70050) t);

-- 当前由管理员在“审查复核”直接处理，保留任务分配代码和历史数据但隐藏入口。
UPDATE system_menu SET status=1, visible=b'0' WHERE id=70050;
