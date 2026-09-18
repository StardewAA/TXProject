-- 商品审核增量结构：备份后在业务库执行，应用升级前执行；不批量下架、不自动批准、不改租户/账号/库存。
-- 存量 review_status 保持 NULL，仅明确 KEEP_EXISTING_UNMODIFIED 策略允许原已上架未修改商品过渡销售。
-- 新插入数据默认草稿；回滚应用不得删除审核记录或把草稿重新当作在售。
SET @mall_review_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_spu' AND COLUMN_NAME = 'review_status') = 0,
 'ALTER TABLE product_spu ADD COLUMN review_status INT NULL DEFAULT NULL COMMENT ''审核状态，NULL为存量过渡，0草稿10待审20通过30驳回''', 'SELECT ''review_status exists''');
PREPARE mall_review_stmt FROM @mall_review_ddl;
EXECUTE mall_review_stmt;
DEALLOCATE PREPARE mall_review_stmt;

-- 只新增审核权限定义，不给经营角色批量授权。普通审核员需由管理员明确授权。
-- 按现有 SPU 查询按钮定位唯一父菜单，不硬编码业务菜单 ID；有歧义则不插入，需人工核对。
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,
 status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '商品审核','product:spu:review',3,90,MIN(parent_id),'','','',NULL,
 0,b'1',b'1',b'1','mall-review-migration',NOW(),'mall-review-migration',NOW(),b'0'
FROM system_menu
WHERE permission='product:spu:query' AND deleted=b'0'
HAVING COUNT(DISTINCT parent_id)=1 AND NOT EXISTS (
 SELECT 1 FROM (SELECT permission FROM system_menu) existing WHERE existing.permission='product:spu:review');

SET @mall_review_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_spu' AND COLUMN_NAME = 'content_version') = 0,
 'ALTER TABLE product_spu ADD COLUMN content_version BIGINT NOT NULL DEFAULT 0 COMMENT ''经营资料版本，旧商品0''', 'SELECT ''content_version exists''');
PREPARE mall_review_stmt FROM @mall_review_ddl;
EXECUTE mall_review_stmt;
DEALLOCATE PREPARE mall_review_stmt;

SET @mall_review_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_spu' AND COLUMN_NAME = 'approved_version') = 0,
 'ALTER TABLE product_spu ADD COLUMN approved_version BIGINT NULL COMMENT ''最后有效批准版本，不回填历史商品''', 'SELECT ''approved_version exists''');
PREPARE mall_review_stmt FROM @mall_review_ddl;
EXECUTE mall_review_stmt;
DEALLOCATE PREPARE mall_review_stmt;

SET @mall_review_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_spu' AND COLUMN_NAME = 'submission_id') = 0,
 'ALTER TABLE product_spu ADD COLUMN submission_id VARCHAR(64) NULL COMMENT ''当前送审批次，撤回重提生成新值''', 'SELECT ''submission_id exists''');
PREPARE mall_review_stmt FROM @mall_review_ddl;
EXECUTE mall_review_stmt;
DEALLOCATE PREPARE mall_review_stmt;

SET @mall_review_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_spu' AND COLUMN_NAME = 'review_remark') = 0,
 'ALTER TABLE product_spu ADD COLUMN review_remark VARCHAR(1000) NULL COMMENT ''当前审核意见''', 'SELECT ''review_remark exists''');
PREPARE mall_review_stmt FROM @mall_review_ddl;
EXECUTE mall_review_stmt;
DEALLOCATE PREPARE mall_review_stmt;

-- 只改列默认值，不 UPDATE 存量行；旧二进制新建行也不得获得历史过渡资格。
ALTER TABLE product_spu ALTER COLUMN review_status SET DEFAULT 0;

CREATE TABLE IF NOT EXISTS product_spu_review_record (
 id BIGINT NOT NULL AUTO_INCREMENT,
 tenant_id BIGINT NOT NULL,
 spu_id BIGINT NOT NULL,
 shop_id BIGINT NULL,
 content_version BIGINT NOT NULL,
 submission_id VARCHAR(64) NOT NULL,
 action VARCHAR(32) NOT NULL,
 actor_type INT NOT NULL,
 actor_id BIGINT NOT NULL,
 remark VARCHAR(1000) NOT NULL DEFAULT '',
 snapshot LONGTEXT NOT NULL,
 create_time DATETIME NOT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_spu_review_event (tenant_id, spu_id, submission_id, action),
 KEY idx_spu_review_history (tenant_id, spu_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='追加式商品经营与审核快照，不随商品删除';

SET @mall_review_ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_spu' AND INDEX_NAME = 'idx_spu_review_queue') = 0,
 'CREATE INDEX idx_spu_review_queue ON product_spu (tenant_id, shop_id, review_status, status)', 'SELECT ''idx_spu_review_queue exists''');
PREPARE mall_review_stmt FROM @mall_review_ddl;
EXECUTE mall_review_stmt;
DEALLOCATE PREPARE mall_review_stmt;
