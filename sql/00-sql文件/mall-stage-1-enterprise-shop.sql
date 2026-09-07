-- 第一阶段：企业入驻与基础资料（商城）
-- 仅涉及 product_shop、product_spu.shop_id 及商城菜单权限。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS product_shop (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '店铺编号',
  enterprise_id bigint NOT NULL COMMENT '企业名录编号',
  name varchar(64) NOT NULL COMMENT '店铺名称',
  logo_url varchar(512) DEFAULT NULL COMMENT '店铺 Logo',
  contact_name varchar(32) DEFAULT NULL COMMENT '联系人',
  contact_mobile varchar(32) DEFAULT NULL COMMENT '联系电话',
  manager_user_id bigint DEFAULT NULL COMMENT '店铺负责人（后台用户编号）',
  introduction varchar(1024) DEFAULT NULL COMMENT '店铺简介',
  status tinyint NOT NULL DEFAULT 0 COMMENT '状态（0正常 1停用）',
  tenant_id bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_shop_enterprise (enterprise_id, deleted),
  KEY idx_product_shop_manager_user (manager_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产业链企业店铺';

SET @shop_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_spu' AND COLUMN_NAME = 'shop_id'
);
SET @shop_column_sql = IF(@shop_column_exists = 0,
  'ALTER TABLE product_spu ADD COLUMN shop_id bigint DEFAULT NULL COMMENT ''所属店铺编号'' AFTER id',
  'SELECT 1');
PREPARE shop_column_stmt FROM @shop_column_sql;
EXECUTE shop_column_stmt;
DEALLOCATE PREPARE shop_column_stmt;

SET @shop_index_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_spu' AND INDEX_NAME = 'idx_product_spu_shop_id'
);
SET @shop_index_sql = IF(@shop_index_exists = 0,
  'CREATE INDEX idx_product_spu_shop_id ON product_spu (shop_id)',
  'SELECT 1');
PREPARE shop_index_stmt FROM @shop_index_sql;
EXECUTE shop_index_stmt;
DEALLOCATE PREPARE shop_index_stmt;

SET @mall_parent_id = (
  SELECT id FROM system_menu
  WHERE (path = 'mall' OR name = '商城系统') AND type = 1 AND deleted = b'0'
  ORDER BY id LIMIT 1
);
SET @mall_parent_id = IFNULL(@mall_parent_id, 2000);

INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 25000,'企业店铺','',2,5,@mall_parent_id,'shop','ep:shop','mall/product/shop/index','ProductShop',0,b'1',b'1',b'1','system',NOW(),'system',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=25000 AND deleted=b'0');

INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 25001,'店铺查询','product:shop:query',3,1,25000,'','','','',0,b'1',b'1',b'1','system',NOW(),'system',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=25001 AND deleted=b'0');

INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 25002,'店铺创建','product:shop:create',3,2,25000,'','','','',0,b'1',b'1',b'1','system',NOW(),'system',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=25002 AND deleted=b'0');

INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 25003,'店铺更新','product:shop:update',3,3,25000,'','','','',0,b'1',b'1',b'1','system',NOW(),'system',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=25003 AND deleted=b'0');

INSERT INTO system_menu
(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT 25004,'店铺删除','product:shop:delete',3,4,25000,'','','','',0,b'1',b'1',b'1','system',NOW(),'system',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=25004 AND deleted=b'0');
