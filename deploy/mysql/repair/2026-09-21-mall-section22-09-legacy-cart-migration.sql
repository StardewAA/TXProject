-- 第 22.9/22.10 节：旧租户购物车安全迁移到平台购物车
--
-- 安全边界：
-- 1. 只读取 trade_cart，不删除、不更新旧购物车；
-- 2. 永久拒绝迁移租户 3000；
-- 3. 仅接受 READY 平台账号映射和启用的平台账号；
-- 4. 不信任 trade_cart.spu_id 快照，归属必须由 SKU -> SPU -> 店铺 -> 企业实时链路确认，
--    且链路各层 tenant_id 必须与旧购物车源租户一致；
-- 5. 每条旧购物车写入迁移台账，已 MIGRATED 的源记录永不重复累加；
-- 6. 无法安全迁移时只记录内部编号、原因码和数量，不保存手机号、登录名、姓名等敏感信息。
--
-- 运行要求：先执行 2026-09-21-mall-section22-platform.sql，并在迁移期间暂停旧购物车和
-- 平台购物车写入。脚本内的命名锁只防止本脚本并发执行，不能代替业务写暂停。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS trade_platform_cart_legacy_migration (
  id bigint NOT NULL AUTO_INCREMENT,
  source_tenant_id bigint NOT NULL,
  legacy_cart_id bigint NOT NULL,
  platform_member_id bigint NULL,
  resolved_tenant_id bigint NULL,
  resolved_enterprise_id bigint NULL,
  resolved_shop_id bigint NULL,
  resolved_spu_id bigint NULL,
  resolved_sku_id bigint NULL,
  source_quantity int NULL,
  source_selected bit(1) NULL,
  source_update_time datetime NULL,
  legacy_spu_mismatch bit(1) NOT NULL DEFAULT b'0',
  migration_status varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT 'MIGRATED/BLOCKED/IGNORED',
  failure_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  target_platform_cart_id bigint NULL,
  migrated_at datetime NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_legacy_cart_source (source_tenant_id,legacy_cart_id),
  KEY idx_legacy_cart_status (migration_status,failure_code),
  KEY idx_legacy_cart_target (target_platform_cart_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='旧购物车迁移平台购物车台账';

DROP TEMPORARY TABLE IF EXISTS tmp_mall_s22_cart_lock;
CREATE TEMPORARY TABLE tmp_mall_s22_cart_lock (
  acquired tinyint NOT NULL,
  CONSTRAINT chk_mall_s22_cart_lock CHECK (acquired=1)
) ENGINE=MEMORY;
INSERT INTO tmp_mall_s22_cart_lock(acquired)
VALUES (GET_LOCK('mall-section22-legacy-cart-migration',30));

-- 临时表必须在事务开始前完成定义。MySQL 的 ALTER/CREATE TABLE 会隐式提交，
-- 因此事务内只允许 DML，确保购物车数量合并与迁移台账原子落库。
DROP TEMPORARY TABLE IF EXISTS tmp_mall_s22_legacy_cart;
CREATE TEMPORARY TABLE tmp_mall_s22_legacy_cart (
  legacy_cart_id bigint NOT NULL,
  source_tenant_id bigint NOT NULL,
  legacy_spu_id bigint NULL,
  legacy_sku_id bigint NULL,
  source_quantity int NULL,
  source_selected bit(1) NULL,
  source_create_time datetime NULL,
  source_update_time datetime NULL,
  platform_account_id bigint NULL,
  resolved_tenant_id bigint NULL,
  resolved_spu_id bigint NULL,
  resolved_shop_id bigint NULL,
  resolved_enterprise_id bigint NULL,
  legacy_spu_mismatch bit(1) NOT NULL DEFAULT b'0',
  failure_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  invalid_reason varchar(255) NULL,
  already_migrated bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (source_tenant_id,legacy_cart_id),
  KEY idx_tmp_cart_group
    (platform_account_id,resolved_tenant_id,resolved_shop_id,legacy_sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS tmp_mall_s22_legacy_cart_group;
CREATE TEMPORARY TABLE tmp_mall_s22_legacy_cart_group (
  platform_account_id bigint NOT NULL,
  tenant_id bigint NOT NULL,
  enterprise_id bigint NOT NULL,
  shop_id bigint NOT NULL,
  spu_id bigint NOT NULL,
  sku_id bigint NOT NULL,
  source_quantity bigint NOT NULL,
  source_selected tinyint unsigned NOT NULL,
  source_create_time datetime NULL,
  invalid_reason varchar(255) NULL,
  source_row_count bigint unsigned NOT NULL,
  target_row_count bigint unsigned NULL,
  matching_target_id bigint unsigned NULL,
  target_quantity int NULL,
  failure_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (platform_account_id,tenant_id,enterprise_id,shop_id,spu_id,sku_id),
  KEY idx_tmp_cart_target (platform_account_id,sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
START TRANSACTION;

-- 已阻塞但后来被软删除的源记录退出当前失败清单；成功历史仍永久保留。
UPDATE trade_platform_cart_legacy_migration migration
LEFT JOIN trade_cart cart
  ON cart.tenant_id=migration.source_tenant_id AND cart.id=migration.legacy_cart_id
SET migration.migration_status='IGNORED',
    migration.failure_code='SOURCE_DELETED_OR_MISSING',
    migration.updater='mall-section22-cart-migration',migration.update_time=NOW()
WHERE migration.deleted=b'0' AND migration.migration_status='BLOCKED'
  AND (cart.id IS NULL OR cart.deleted=b'1');

INSERT INTO tmp_mall_s22_legacy_cart
 (legacy_cart_id,source_tenant_id,legacy_spu_id,legacy_sku_id,source_quantity,
  source_selected,source_create_time,source_update_time,platform_account_id,
  resolved_tenant_id,resolved_spu_id,resolved_shop_id,resolved_enterprise_id,
  legacy_spu_mismatch,failure_code,invalid_reason,already_migrated)
SELECT cart.id AS legacy_cart_id,
       cart.tenant_id AS source_tenant_id,
       cart.spu_id AS legacy_spu_id,
       cart.sku_id AS legacy_sku_id,
       cart.count AS source_quantity,
       cart.selected AS source_selected,
       cart.create_time AS source_create_time,
       cart.update_time AS source_update_time,
       account_link.platform_account_id,
       sku.tenant_id AS resolved_tenant_id,
       sku.spu_id AS resolved_spu_id,
       spu.shop_id AS resolved_shop_id,
       shop.enterprise_id AS resolved_enterprise_id,
       CASE WHEN sku.id IS NOT NULL AND cart.spu_id<>sku.spu_id THEN b'1' ELSE b'0' END
         AS legacy_spu_mismatch,
       CASE
         WHEN cart.tenant_id=3000 THEN 'SOURCE_TENANT_RESERVED'
         WHEN cart.count IS NULL OR cart.count<1 OR cart.count>99999 THEN 'SOURCE_QUANTITY_INVALID'
         WHEN account_link.id IS NULL OR account_link.link_status<>'READY'
           OR account_link.platform_account_id IS NULL THEN 'PLATFORM_LINK_NOT_READY'
         WHEN platform_account.id IS NULL OR platform_account.deleted=b'1'
           OR platform_account.status<>0 THEN 'PLATFORM_ACCOUNT_NOT_READY'
         WHEN sku.id IS NULL THEN 'SKU_NOT_FOUND'
         WHEN sku.tenant_id IS NULL OR sku.tenant_id<=0 OR sku.tenant_id=3000
           OR sku.tenant_id<>cart.tenant_id THEN 'SKU_TENANT_MISMATCH'
         WHEN sku.spu_id IS NULL OR sku.spu_id<=0 OR spu.id IS NULL THEN 'SPU_NOT_FOUND'
         WHEN spu.tenant_id<>sku.tenant_id THEN 'SPU_TENANT_MISMATCH'
         WHEN spu.shop_id IS NULL OR spu.shop_id<=0 OR shop.id IS NULL THEN 'SHOP_NOT_FOUND'
         WHEN shop.tenant_id<>sku.tenant_id THEN 'SHOP_TENANT_MISMATCH'
         WHEN shop.enterprise_id IS NULL OR shop.enterprise_id<=0 OR enterprise.id IS NULL
           THEN 'ENTERPRISE_NOT_FOUND'
         WHEN enterprise.tenant_id<>sku.tenant_id THEN 'ENTERPRISE_TENANT_MISMATCH'
         ELSE NULL
       END AS failure_code,
       CASE
         WHEN portal_tenant.id IS NULL OR portal_tenant.deleted=b'1' OR portal_tenant.open_status<>0
           OR system_tenant.id IS NULL OR system_tenant.deleted=b'1' OR system_tenant.status<>0
           OR system_tenant.expire_time<=NOW() THEN '所属租户未开放或商品不存在'
         WHEN sku.deleted=b'1' THEN '商品规格已失效'
         WHEN spu.deleted=b'1' THEN '商品已删除'
         WHEN shop.deleted=b'1' OR shop.status<>0 THEN '店铺已关闭'
         WHEN enterprise.deleted=b'1' OR enterprise.audit_status<>1 THEN '供应企业资质无效'
         WHEN spu.status<>1 THEN '商品已下架'
         WHEN spu.review_status<>20 OR spu.approved_version IS NULL
           OR spu.approved_version<>spu.content_version THEN '商品审核未通过或已变更'
         WHEN sku.stock IS NULL OR sku.stock<=0 THEN '库存不足'
         WHEN sku.price IS NULL OR sku.price<=0 THEN '商品价格无效'
         ELSE NULL
       END AS invalid_reason,
       CASE WHEN migrated.migration_status='MIGRATED' AND migrated.deleted=b'0'
         THEN b'1' ELSE b'0' END AS already_migrated
FROM trade_cart cart
LEFT JOIN member_platform_account_link account_link
  ON account_link.source_tenant_id=cart.tenant_id
 AND account_link.legacy_member_id=cart.user_id AND account_link.deleted=b'0'
LEFT JOIN member_platform_account platform_account
  ON platform_account.id=account_link.platform_account_id
LEFT JOIN product_sku sku ON sku.id=cart.sku_id
LEFT JOIN product_spu spu ON spu.id=sku.spu_id
LEFT JOIN product_shop shop ON shop.id=spu.shop_id
LEFT JOIN product_enterprise enterprise ON enterprise.id=shop.enterprise_id
LEFT JOIN mall_portal_tenant portal_tenant
  ON portal_tenant.tenant_id=sku.tenant_id AND portal_tenant.deleted=b'0'
LEFT JOIN system_tenant system_tenant ON system_tenant.id=sku.tenant_id
LEFT JOIN trade_platform_cart_legacy_migration migrated
  ON migrated.source_tenant_id=cart.tenant_id AND migrated.legacy_cart_id=cart.id
WHERE cart.deleted=b'0';

-- 先按真实归属聚合尚未成功迁移的源行。重复旧行只增加一次总数量。
INSERT INTO tmp_mall_s22_legacy_cart_group
 (platform_account_id,tenant_id,enterprise_id,shop_id,spu_id,sku_id,
  source_quantity,source_selected,source_create_time,invalid_reason,source_row_count,
  target_row_count,matching_target_id,target_quantity,failure_code)
SELECT candidate.platform_account_id,
       candidate.resolved_tenant_id AS tenant_id,
       candidate.resolved_enterprise_id AS enterprise_id,
       candidate.resolved_shop_id AS shop_id,
       candidate.resolved_spu_id AS spu_id,
       candidate.legacy_sku_id AS sku_id,
       SUM(candidate.source_quantity) AS source_quantity,
       MAX(candidate.source_selected+0) AS source_selected,
       MIN(candidate.source_create_time) AS source_create_time,
       MAX(candidate.invalid_reason) AS invalid_reason,
       COUNT(*) AS source_row_count,
       CAST(NULL AS UNSIGNED) AS target_row_count,
       CAST(NULL AS UNSIGNED) AS matching_target_id,
       CAST(NULL AS SIGNED) AS target_quantity,
       CAST(NULL AS CHAR(64)) AS failure_code
FROM tmp_mall_s22_legacy_cart candidate
WHERE candidate.failure_code IS NULL AND candidate.already_migrated=b'0'
GROUP BY candidate.platform_account_id,candidate.resolved_tenant_id,
         candidate.resolved_enterprise_id,candidate.resolved_shop_id,
         candidate.resolved_spu_id,candidate.legacy_sku_id;

UPDATE tmp_mall_s22_legacy_cart_group cart_group
SET cart_group.target_row_count=(
      SELECT COUNT(*) FROM trade_platform_cart target
      WHERE target.platform_member_id=cart_group.platform_account_id
        AND target.sku_id=cart_group.sku_id AND target.deleted=b'0'),
    cart_group.matching_target_id=(
      SELECT MIN(target.id) FROM trade_platform_cart target
      WHERE target.platform_member_id=cart_group.platform_account_id
        AND target.tenant_id=cart_group.tenant_id
        AND target.enterprise_id=cart_group.enterprise_id
        AND target.shop_id=cart_group.shop_id
        AND target.spu_id=cart_group.spu_id
        AND target.sku_id=cart_group.sku_id AND target.deleted=b'0'),
    cart_group.target_quantity=(
      SELECT MAX(target.quantity) FROM trade_platform_cart target
      WHERE target.platform_member_id=cart_group.platform_account_id
        AND target.tenant_id=cart_group.tenant_id
        AND target.enterprise_id=cart_group.enterprise_id
        AND target.shop_id=cart_group.shop_id
        AND target.spu_id=cart_group.spu_id
        AND target.sku_id=cart_group.sku_id AND target.deleted=b'0');

UPDATE tmp_mall_s22_legacy_cart_group cart_group
SET cart_group.failure_code=CASE
  WHEN cart_group.source_quantity<1 OR cart_group.source_quantity>99999
    THEN 'MERGED_QUANTITY_INVALID'
  WHEN cart_group.target_row_count>1 THEN 'TARGET_CART_AMBIGUOUS'
  WHEN cart_group.target_row_count=1 AND cart_group.matching_target_id IS NULL
    THEN 'TARGET_CART_OWNERSHIP_MISMATCH'
  WHEN cart_group.matching_target_id IS NOT NULL
    AND (cart_group.target_quantity IS NULL OR cart_group.target_quantity<1
      OR cart_group.target_quantity>99999) THEN 'TARGET_CART_QUANTITY_INVALID'
  WHEN cart_group.matching_target_id IS NOT NULL
    AND cart_group.target_quantity>99999-cart_group.source_quantity THEN 'TARGET_CART_QUANTITY_LIMIT'
  ELSE NULL
END;

-- 将组级冲突落回每条源记录，便于逐条重试或人工处理。
UPDATE tmp_mall_s22_legacy_cart candidate
JOIN tmp_mall_s22_legacy_cart_group cart_group
  ON cart_group.platform_account_id=candidate.platform_account_id
 AND cart_group.tenant_id=candidate.resolved_tenant_id
 AND cart_group.enterprise_id=candidate.resolved_enterprise_id
 AND cart_group.shop_id=candidate.resolved_shop_id
 AND cart_group.spu_id=candidate.resolved_spu_id
 AND cart_group.sku_id=candidate.legacy_sku_id
SET candidate.failure_code=cart_group.failure_code
WHERE candidate.failure_code IS NULL AND candidate.already_migrated=b'0'
  AND cart_group.failure_code IS NOT NULL;

-- BLOCKED 台账不复制用户标识或凭据；后续修复归属后重跑即可转为 MIGRATED。
INSERT INTO trade_platform_cart_legacy_migration
 (source_tenant_id,legacy_cart_id,platform_member_id,resolved_tenant_id,
  resolved_enterprise_id,resolved_shop_id,resolved_spu_id,resolved_sku_id,
  source_quantity,source_selected,source_update_time,legacy_spu_mismatch,
  migration_status,failure_code,target_platform_cart_id,migrated_at,
  creator,updater,deleted)
SELECT candidate.source_tenant_id,candidate.legacy_cart_id,candidate.platform_account_id,
       candidate.resolved_tenant_id,candidate.resolved_enterprise_id,
       candidate.resolved_shop_id,candidate.resolved_spu_id,candidate.legacy_sku_id,
       candidate.source_quantity,candidate.source_selected,candidate.source_update_time,
       candidate.legacy_spu_mismatch,'BLOCKED',candidate.failure_code,NULL,NULL,
       'mall-section22-cart-migration','mall-section22-cart-migration',b'0'
FROM tmp_mall_s22_legacy_cart candidate
WHERE candidate.failure_code IS NOT NULL AND candidate.already_migrated=b'0'
ON DUPLICATE KEY UPDATE
 platform_member_id=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.platform_member_id,VALUES(platform_member_id)),
 resolved_tenant_id=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.resolved_tenant_id,VALUES(resolved_tenant_id)),
 resolved_enterprise_id=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.resolved_enterprise_id,VALUES(resolved_enterprise_id)),
 resolved_shop_id=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.resolved_shop_id,VALUES(resolved_shop_id)),
 resolved_spu_id=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.resolved_spu_id,VALUES(resolved_spu_id)),
 resolved_sku_id=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.resolved_sku_id,VALUES(resolved_sku_id)),
 source_quantity=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.source_quantity,VALUES(source_quantity)),
 source_selected=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.source_selected,VALUES(source_selected)),
 source_update_time=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.source_update_time,VALUES(source_update_time)),
 legacy_spu_mismatch=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.legacy_spu_mismatch,VALUES(legacy_spu_mismatch)),
 migration_status=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED','MIGRATED','BLOCKED'),
 failure_code=IF(trade_platform_cart_legacy_migration.migration_status='MIGRATED',
   trade_platform_cart_legacy_migration.failure_code,VALUES(failure_code)),
 updater='mall-section22-cart-migration',update_time=NOW(),deleted=b'0';

-- 新条目复制，已存在且真实归属完全一致的条目按数量合并；不覆盖用户当前勾选状态。
INSERT INTO trade_platform_cart
 (platform_member_id,tenant_id,enterprise_id,shop_id,spu_id,sku_id,quantity,
  selected,invalid_reason,creator,create_time,updater,update_time,deleted)
SELECT cart_group.platform_account_id,cart_group.tenant_id,cart_group.enterprise_id,
       cart_group.shop_id,cart_group.spu_id,cart_group.sku_id,cart_group.source_quantity,
       IF(cart_group.source_selected<>0,b'1',b'0'),cart_group.invalid_reason,
       'mall-section22-cart-migration',cart_group.source_create_time,
       'mall-section22-cart-migration',NOW(),b'0'
FROM tmp_mall_s22_legacy_cart_group cart_group
WHERE cart_group.failure_code IS NULL
ON DUPLICATE KEY UPDATE
 quantity=trade_platform_cart.quantity+VALUES(quantity),
 invalid_reason=VALUES(invalid_reason),
 updater='mall-section22-cart-migration',update_time=NOW();

INSERT INTO trade_platform_cart_legacy_migration
 (source_tenant_id,legacy_cart_id,platform_member_id,resolved_tenant_id,
  resolved_enterprise_id,resolved_shop_id,resolved_spu_id,resolved_sku_id,
  source_quantity,source_selected,source_update_time,legacy_spu_mismatch,
  migration_status,failure_code,target_platform_cart_id,migrated_at,
  creator,updater,deleted)
SELECT candidate.source_tenant_id,candidate.legacy_cart_id,candidate.platform_account_id,
       candidate.resolved_tenant_id,candidate.resolved_enterprise_id,
       candidate.resolved_shop_id,candidate.resolved_spu_id,candidate.legacy_sku_id,
       candidate.source_quantity,candidate.source_selected,candidate.source_update_time,
       candidate.legacy_spu_mismatch,'MIGRATED',NULL,target.id,NOW(),
       'mall-section22-cart-migration','mall-section22-cart-migration',b'0'
FROM tmp_mall_s22_legacy_cart candidate
JOIN tmp_mall_s22_legacy_cart_group cart_group
  ON cart_group.platform_account_id=candidate.platform_account_id
 AND cart_group.tenant_id=candidate.resolved_tenant_id
 AND cart_group.enterprise_id=candidate.resolved_enterprise_id
 AND cart_group.shop_id=candidate.resolved_shop_id
 AND cart_group.spu_id=candidate.resolved_spu_id
 AND cart_group.sku_id=candidate.legacy_sku_id
JOIN trade_platform_cart target
  ON target.platform_member_id=cart_group.platform_account_id
 AND target.tenant_id=cart_group.tenant_id
 AND target.enterprise_id=cart_group.enterprise_id
 AND target.shop_id=cart_group.shop_id
 AND target.spu_id=cart_group.spu_id
 AND target.sku_id=cart_group.sku_id AND target.deleted=b'0'
WHERE candidate.failure_code IS NULL AND candidate.already_migrated=b'0'
  AND cart_group.failure_code IS NULL
ON DUPLICATE KEY UPDATE
 platform_member_id=VALUES(platform_member_id),resolved_tenant_id=VALUES(resolved_tenant_id),
 resolved_enterprise_id=VALUES(resolved_enterprise_id),resolved_shop_id=VALUES(resolved_shop_id),
 resolved_spu_id=VALUES(resolved_spu_id),resolved_sku_id=VALUES(resolved_sku_id),
 source_quantity=VALUES(source_quantity),source_selected=VALUES(source_selected),
 source_update_time=VALUES(source_update_time),legacy_spu_mismatch=VALUES(legacy_spu_mismatch),
 migration_status='MIGRATED',failure_code=NULL,target_platform_cart_id=VALUES(target_platform_cart_id),
 migrated_at=COALESCE(migrated_at,VALUES(migrated_at)),
 updater='mall-section22-cart-migration',update_time=NOW(),deleted=b'0';

COMMIT;
DO RELEASE_LOCK('mall-section22-legacy-cart-migration');

-- 非敏感验收输出：不返回会员编号、登录名、手机号或姓名。
SELECT migration_status,COALESCE(failure_code,'OK') AS result_code,COUNT(*) AS source_rows,
       COALESCE(SUM(source_quantity),0) AS source_quantity
FROM trade_platform_cart_legacy_migration
WHERE deleted=b'0'
GROUP BY migration_status,COALESCE(failure_code,'OK')
ORDER BY migration_status,result_code;

SELECT COUNT(*) AS active_legacy_rows,
       SUM(tenant_id=3000) AS reserved_tenant_rows,
       SUM(tenant_id<>3000) AS eligible_tenant_rows
FROM trade_cart WHERE deleted=b'0';

SELECT COUNT(*) AS active_platform_cart_rows,
       COUNT(DISTINCT CONCAT(platform_member_id,':',sku_id)) AS platform_member_sku_groups,
       SUM(tenant_id=3000) AS forbidden_tenant_rows
FROM trade_platform_cart WHERE deleted=b'0';

SELECT COUNT(*) AS source_changed_after_migration
FROM trade_platform_cart_legacy_migration migration
JOIN trade_cart cart
  ON cart.tenant_id=migration.source_tenant_id AND cart.id=migration.legacy_cart_id
WHERE migration.deleted=b'0' AND migration.migration_status='MIGRATED'
  AND (cart.deleted=b'1' OR NOT (cart.count<=>migration.source_quantity)
    OR NOT (cart.selected<=>migration.source_selected)
    OR NOT (cart.update_time<=>migration.source_update_time));
