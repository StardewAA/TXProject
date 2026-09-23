-- 第 22.11 节：退役公共租户 3000 核心商城资源隔离盘点。
--
-- 安全边界：
-- 1. 本脚本只向 mall_public_tenant_migration 写入 QUARANTINED 审计清单；
-- 2. 不迁移、不删除、不更新任何来源业务表，也不改订单、支付、退款、结算等资金事实；
-- 3. rollback_payload 仅保存资源归属、显示名称和父级 ID，不保存密码、Token、手机号、
--    联系人、地址、证照号码、询价消息正文、订单金额或支付渠道报文；
-- 4. 重复执行不会新增重复清单，也不会覆盖已经人工确认的目标租户/目标资源或迁移状态。
--
-- 部署顺序：可独立执行；若第 22 节主脚本尚未执行，本脚本会先创建审计表。
SET NAMES utf8mb4;

SET @s2211_source_tenant_id := 3000;
SET @s2211_batch_no := 'S22.11-3000-INVENTORY-20260921';
SET @s2211_operator := 'mall-section22.11';
SET @s2211_reason := '退役公共租户 3000 资源仅隔离盘点；真实目标租户尚未人工确认';

CREATE TABLE IF NOT EXISTS mall_public_tenant_migration (
  id bigint NOT NULL AUTO_INCREMENT,
  batch_no varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  resource_type varchar(32) NOT NULL,
  source_tenant_id bigint NOT NULL,
  source_resource_id bigint NOT NULL,
  target_tenant_id bigint NULL,
  target_resource_id bigint NULL,
  migration_status varchar(24) NOT NULL COMMENT 'PENDING/MIGRATED/QUARANTINED/FAILED',
  failure_reason varchar(512) NULL,
  verified_by bigint NULL,
  verified_at datetime NULL,
  rollback_payload json NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_public_migration
    (batch_no,resource_type,source_tenant_id,source_resource_id,deleted),
  KEY idx_public_migration_status (migration_status,resource_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原公共租户业务迁移审计';

-- ---------------------------------------------------------------------------
-- A. 部署前结构验证结果集。missing_table_count 必须为 0。
-- ---------------------------------------------------------------------------
SELECT 'S22.11_REQUIRED_TABLES' AS check_name,
       COUNT(*) AS required_table_count,
       SUM(IF(t.table_name IS NULL,1,0)) AS missing_table_count,
       GROUP_CONCAT(IF(t.table_name IS NULL,r.table_name,NULL)
                    ORDER BY r.table_name SEPARATOR ',') AS missing_tables
FROM (
  SELECT 'member_user' AS table_name
  UNION ALL SELECT 'member_address'
  UNION ALL SELECT 'member_mall_customer_affiliation'
  UNION ALL SELECT 'member_platform_account_link'
  UNION ALL SELECT 'member_platform_tenant_membership'
  UNION ALL SELECT 'product_enterprise_application'
  UNION ALL SELECT 'product_enterprise'
  UNION ALL SELECT 'product_enterprise_admin_member'
  UNION ALL SELECT 'product_shop'
  UNION ALL SELECT 'product_shop_admin_authorization'
  UNION ALL SELECT 'product_shop_return_address'
  UNION ALL SELECT 'product_brand'
  UNION ALL SELECT 'product_category'
  UNION ALL SELECT 'product_property'
  UNION ALL SELECT 'product_property_value'
  UNION ALL SELECT 'product_spu'
  UNION ALL SELECT 'product_sku'
  UNION ALL SELECT 'product_comment'
  UNION ALL SELECT 'product_favorite'
  UNION ALL SELECT 'product_browse_history'
  UNION ALL SELECT 'product_comparison_list'
  UNION ALL SELECT 'product_spu_review_record'
  UNION ALL SELECT 'product_supplier_stock_adjustment'
  UNION ALL SELECT 'product_purchase_inquiry'
  UNION ALL SELECT 'product_purchase_inquiry_item'
  UNION ALL SELECT 'product_purchase_inquiry_invited_shop'
  UNION ALL SELECT 'product_purchase_inquiry_invited_scope'
  UNION ALL SELECT 'product_purchase_inquiry_quote'
  UNION ALL SELECT 'product_purchase_inquiry_quote_item'
  UNION ALL SELECT 'product_purchase_inquiry_quote_message'
  UNION ALL SELECT 'trade_delivery_express_template'
  UNION ALL SELECT 'trade_delivery_express_template_charge'
  UNION ALL SELECT 'trade_delivery_express_template_free'
  UNION ALL SELECT 'promotion_kefu_agent'
  UNION ALL SELECT 'wms_item'
  UNION ALL SELECT 'wms_item_brand'
  UNION ALL SELECT 'wms_item_category'
  UNION ALL SELECT 'wms_item_sku'
  UNION ALL SELECT 'wms_inventory'
  UNION ALL SELECT 'wms_warehouse'
  UNION ALL SELECT 'trade_order'
  UNION ALL SELECT 'trade_order_item'
  UNION ALL SELECT 'pay_order'
  UNION ALL SELECT 'pay_order_extension'
  UNION ALL SELECT 'pay_refund'
  UNION ALL SELECT 'trade_after_sale'
  UNION ALL SELECT 'trade_after_sale_log'
  UNION ALL SELECT 'trade_cart'
  UNION ALL SELECT 'trade_platform_cart'
  UNION ALL SELECT 'trade_platform_checkout_order'
) r
LEFT JOIN information_schema.tables t
  ON t.table_schema=DATABASE() AND t.table_name=r.table_name;

-- 临时源清单只在当前连接存在；主键可在写审计表之前暴露资源键碰撞。
DROP TEMPORARY TABLE IF EXISTS tmp_s2211_tenant3000_inventory;
CREATE TEMPORARY TABLE tmp_s2211_tenant3000_inventory (
  resource_type varchar(32) NOT NULL,
  source_resource_id bigint NOT NULL,
  rollback_payload json NULL,
  PRIMARY KEY (resource_type,source_resource_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- B. 会员、平台映射和企业/店铺归属。
-- ---------------------------------------------------------------------------
INSERT INTO tmp_s2211_tenant3000_inventory(resource_type,source_resource_id,rollback_payload)
SELECT 'MEMBER',u.id,
       JSON_OBJECT('tenantId',u.tenant_id,'memberIdentity',u.member_identity)
FROM member_user u
WHERE u.tenant_id=@s2211_source_tenant_id AND u.deleted=b'0'
UNION ALL
SELECT 'MEMBER_ADDRESS',a.id,
       JSON_OBJECT('tenantId',a.tenant_id,'memberId',a.user_id)
FROM member_address a
WHERE a.tenant_id=@s2211_source_tenant_id AND a.deleted=b'0'
UNION ALL
SELECT 'MEMBER_AFFILIATION',a.id,
       JSON_OBJECT('tenantId',a.tenant_id,'memberId',a.member_id,
                   'serviceEnterpriseId',a.service_enterprise_id)
FROM member_mall_customer_affiliation a
WHERE a.tenant_id=@s2211_source_tenant_id AND a.deleted=b'0'
UNION ALL
SELECT 'PLATFORM_ACCOUNT_LINK',l.id,
       JSON_OBJECT('sourceTenantId',l.source_tenant_id,
                   'platformAccountId',l.platform_account_id,
                   'legacyMemberId',l.legacy_member_id,
                   'linkStatus',l.link_status)
FROM member_platform_account_link l
WHERE l.source_tenant_id=@s2211_source_tenant_id AND l.deleted=b'0'
UNION ALL
SELECT 'PLATFORM_MEMBERSHIP',m.id,
       JSON_OBJECT('tenantId',m.tenant_id,'platformAccountId',m.platform_account_id,
                   'enterpriseId',m.enterprise_id,'memberType',m.member_type)
FROM member_platform_tenant_membership m
WHERE m.tenant_id=@s2211_source_tenant_id AND m.deleted=b'0'
UNION ALL
SELECT 'ENTERPRISE_APPLICATION',a.id,
       JSON_OBJECT('tenantId',a.tenant_id,'name',a.name,
                   'enterpriseId',a.enterprise_id,'shopId',a.shop_id)
FROM product_enterprise_application a
WHERE a.tenant_id=@s2211_source_tenant_id AND a.deleted=b'0'
UNION ALL
SELECT 'ENTERPRISE',e.id,
       JSON_OBJECT('tenantId',e.tenant_id,'name',e.name)
FROM product_enterprise e
WHERE e.tenant_id=@s2211_source_tenant_id AND e.deleted=b'0'
UNION ALL
SELECT 'ENTERPRISE_ADMIN_MEMBER',m.id,
       JSON_OBJECT('tenantId',m.tenant_id,'enterpriseId',m.enterprise_id,
                   'adminUserId',m.admin_user_id,'scopeType',m.scope_type)
FROM product_enterprise_admin_member m
WHERE m.tenant_id=@s2211_source_tenant_id AND m.deleted=b'0'
UNION ALL
SELECT 'SHOP',s.id,
       JSON_OBJECT('tenantId',s.tenant_id,'name',s.name,'enterpriseId',s.enterprise_id)
FROM product_shop s
WHERE s.tenant_id=@s2211_source_tenant_id AND s.deleted=b'0'
UNION ALL
SELECT 'SHOP_ADMIN_AUTH',a.id,
       JSON_OBJECT('tenantId',a.tenant_id,'enterpriseId',a.enterprise_id,
                   'shopId',a.shop_id,'adminUserId',a.admin_user_id)
FROM product_shop_admin_authorization a
WHERE a.tenant_id=@s2211_source_tenant_id AND a.deleted=b'0'
UNION ALL
SELECT 'SHOP_RETURN_ADDRESS',a.shop_id,
       JSON_OBJECT('tenantId',a.tenant_id,'shopId',a.shop_id)
FROM product_shop_return_address a
WHERE a.tenant_id=@s2211_source_tenant_id AND a.deleted=b'0';

-- ---------------------------------------------------------------------------
-- C. 商品目录、内容及用户商品关系。图片 URL、评价正文和审核快照不入审计载荷。
-- ---------------------------------------------------------------------------
INSERT INTO tmp_s2211_tenant3000_inventory(resource_type,source_resource_id,rollback_payload)
SELECT 'BRAND',b.id,
       JSON_OBJECT('tenantId',b.tenant_id,'name',b.name)
FROM product_brand b
WHERE b.tenant_id=@s2211_source_tenant_id AND b.deleted=b'0'
UNION ALL
SELECT 'CATEGORY',c.id,
       JSON_OBJECT('tenantId',c.tenant_id,'name',c.name,'parentId',c.parent_id)
FROM product_category c
WHERE c.tenant_id=@s2211_source_tenant_id AND c.deleted=b'0'
UNION ALL
SELECT 'PROPERTY',p.id,
       JSON_OBJECT('tenantId',p.tenant_id,'name',p.name)
FROM product_property p
WHERE p.tenant_id=@s2211_source_tenant_id AND p.deleted=b'0'
UNION ALL
SELECT 'PROPERTY_VALUE',v.id,
       JSON_OBJECT('tenantId',v.tenant_id,'name',v.name,'propertyId',v.property_id)
FROM product_property_value v
WHERE v.tenant_id=@s2211_source_tenant_id AND v.deleted=b'0'
UNION ALL
SELECT 'SPU',s.id,
       JSON_OBJECT('tenantId',s.tenant_id,'name',s.name,'shopId',s.shop_id,
                   'categoryId',s.category_id,'brandId',s.brand_id,
                   'deliveryTemplateId',s.delivery_template_id)
FROM product_spu s
WHERE s.tenant_id=@s2211_source_tenant_id AND s.deleted=b'0'
UNION ALL
SELECT 'SKU',s.id,
       JSON_OBJECT('tenantId',s.tenant_id,'spuId',s.spu_id,
                   'wmsSkuId',s.wms_sku_id,'wmsWarehouseId',s.wms_warehouse_id)
FROM product_sku s
WHERE s.tenant_id=@s2211_source_tenant_id AND s.deleted=b'0'
UNION ALL
SELECT 'PRODUCT_COMMENT',c.id,
       JSON_OBJECT('tenantId',c.tenant_id,'orderId',c.order_id,'orderItemId',c.order_item_id,
                   'spuId',c.spu_id,'skuId',c.sku_id,'memberId',c.user_id)
FROM product_comment c
WHERE c.tenant_id=@s2211_source_tenant_id AND c.deleted=b'0'
UNION ALL
SELECT 'FAVORITE',f.id,
       JSON_OBJECT('tenantId',f.tenant_id,'memberId',f.user_id,'spuId',f.spu_id)
FROM product_favorite f
WHERE f.tenant_id=@s2211_source_tenant_id AND f.deleted=b'0'
UNION ALL
SELECT 'BROWSE_HISTORY',h.id,
       JSON_OBJECT('tenantId',h.tenant_id,'memberId',h.user_id,'spuId',h.spu_id)
FROM product_browse_history h
WHERE h.tenant_id=@s2211_source_tenant_id AND h.deleted=b'0'
UNION ALL
SELECT 'COMPARISON_LIST',c.id,
       JSON_OBJECT('tenantId',c.tenant_id,'memberId',c.member_id)
FROM product_comparison_list c
WHERE c.tenant_id=@s2211_source_tenant_id
UNION ALL
SELECT 'SPU_REVIEW',r.id,
       JSON_OBJECT('tenantId',r.tenant_id,'spuId',r.spu_id,'shopId',r.shop_id,
                   'contentVersion',r.content_version,'action',r.action)
FROM product_spu_review_record r
WHERE r.tenant_id=@s2211_source_tenant_id
UNION ALL
SELECT 'STOCK_ADJUSTMENT',a.id,
       JSON_OBJECT('tenantId',a.tenant_id,'shopId',a.shop_id,'spuId',a.spu_id,
                   'skuId',a.sku_id,'memberId',a.member_id)
FROM product_supplier_stock_adjustment a
WHERE a.tenant_id=@s2211_source_tenant_id AND a.deleted=b'0';

-- ---------------------------------------------------------------------------
-- D. 询价主从、邀请、报价和沟通记录。联系方式、备注及消息正文不入审计载荷。
-- ---------------------------------------------------------------------------
INSERT INTO tmp_s2211_tenant3000_inventory(resource_type,source_resource_id,rollback_payload)
SELECT 'INQUIRY',i.id,
       JSON_OBJECT('tenantId',i.tenant_id,'title',i.title,'enterpriseId',i.enterprise_id,
                   'selectedQuoteId',i.selected_quote_id,'visibility',i.visibility)
FROM product_purchase_inquiry i
WHERE i.tenant_id=@s2211_source_tenant_id AND i.deleted=b'0'
UNION ALL
SELECT 'INQUIRY_ITEM',i.id,
       JSON_OBJECT('tenantId',i.tenant_id,'inquiryId',i.inquiry_id,
                   'name',i.name,'skuId',i.sku_id)
FROM product_purchase_inquiry_item i
WHERE i.tenant_id=@s2211_source_tenant_id AND i.deleted=b'0'
UNION ALL
SELECT 'INQUIRY_INVITED_SHOP',s.id,
       JSON_OBJECT('tenantId',s.tenant_id,'inquiryId',s.inquiry_id,'shopId',s.shop_id)
FROM product_purchase_inquiry_invited_shop s
WHERE s.tenant_id=@s2211_source_tenant_id AND s.deleted=b'0'
UNION ALL
SELECT 'INQUIRY_INVITED_SCOPE',s.id,
       JSON_OBJECT('tenantId',i.tenant_id,'inquiryId',s.inquiry_id,'targetType',s.target_type,
                   'targetTenantId',s.target_tenant_id,
                   'targetEnterpriseId',s.target_enterprise_id,'targetShopId',s.target_shop_id)
FROM product_purchase_inquiry_invited_scope s
JOIN product_purchase_inquiry i ON i.id=s.inquiry_id AND i.deleted=b'0'
WHERE i.tenant_id=@s2211_source_tenant_id AND s.deleted=b'0'
UNION ALL
SELECT 'INQUIRY_QUOTE',q.id,
       JSON_OBJECT('tenantId',q.tenant_id,'inquiryId',q.inquiry_id,
                   'shopId',q.shop_id,'enterpriseId',q.enterprise_id)
FROM product_purchase_inquiry_quote q
WHERE q.tenant_id=@s2211_source_tenant_id AND q.deleted=b'0'
UNION ALL
SELECT 'INQUIRY_QUOTE_ITEM',i.id,
       JSON_OBJECT('tenantId',i.tenant_id,'quoteId',i.quote_id,
                   'inquiryItemId',i.inquiry_item_id,'skuId',i.sku_id,'name',i.product_name)
FROM product_purchase_inquiry_quote_item i
WHERE i.tenant_id=@s2211_source_tenant_id AND i.deleted=b'0'
UNION ALL
SELECT 'INQUIRY_QUOTE_MESSAGE',m.id,
       JSON_OBJECT('tenantId',m.tenant_id,'quoteId',m.quote_id,
                   'senderRole',m.sender_role,'senderActorType',m.sender_actor_type,
                   'senderActorId',m.sender_actor_id)
FROM product_purchase_inquiry_quote_message m
WHERE m.tenant_id=@s2211_source_tenant_id AND m.deleted=b'0';

-- ---------------------------------------------------------------------------
-- E. 运费模板和客服账号。客服以 actor_type + actor_id 为账号口径去重，
--    不把聊天记录或账号身份信息复制进 rollback_payload。
-- ---------------------------------------------------------------------------
INSERT INTO tmp_s2211_tenant3000_inventory(resource_type,source_resource_id,rollback_payload)
SELECT 'EXPRESS_TEMPLATE',t.id,
       JSON_OBJECT('tenantId',t.tenant_id,'name',t.name)
FROM trade_delivery_express_template t
WHERE t.tenant_id=@s2211_source_tenant_id AND t.deleted=b'0'
UNION ALL
SELECT 'EXPRESS_TEMPLATE_CHARGE',c.id,
       JSON_OBJECT('tenantId',c.tenant_id,'templateId',c.template_id)
FROM trade_delivery_express_template_charge c
WHERE c.tenant_id=@s2211_source_tenant_id AND c.deleted=b'0'
UNION ALL
SELECT 'EXPRESS_TEMPLATE_FREE',f.id,
       JSON_OBJECT('tenantId',f.tenant_id,'templateId',f.template_id)
FROM trade_delivery_express_template_free f
WHERE f.tenant_id=@s2211_source_tenant_id AND f.deleted=b'0'
UNION ALL
SELECT IF(a.actor_type=1,'KEFU_ADMIN_AGENT','KEFU_MEMBER_AGENT'),a.actor_id,
       JSON_OBJECT('tenantId',a.tenant_id,'actorType',a.actor_type)
FROM promotion_kefu_agent a
WHERE a.tenant_id=@s2211_source_tenant_id
GROUP BY a.actor_type,a.actor_id,a.tenant_id;

-- ---------------------------------------------------------------------------
-- F. WMS 只盘点租户 3000 自身资源，不变更物料、库存或仓库数量事实。
-- ---------------------------------------------------------------------------
INSERT INTO tmp_s2211_tenant3000_inventory(resource_type,source_resource_id,rollback_payload)
SELECT 'WMS_ITEM',i.id,
       JSON_OBJECT('tenantId',i.tenant_id,'name',i.name,
                   'categoryId',i.category_id,'brandId',i.brand_id)
FROM wms_item i
WHERE i.tenant_id=@s2211_source_tenant_id AND i.deleted=b'0'
UNION ALL
SELECT 'WMS_BRAND',b.id,
       JSON_OBJECT('tenantId',b.tenant_id,'name',b.name)
FROM wms_item_brand b
WHERE b.tenant_id=@s2211_source_tenant_id AND b.deleted=b'0'
UNION ALL
SELECT 'WMS_CATEGORY',c.id,
       JSON_OBJECT('tenantId',c.tenant_id,'name',c.name,'parentId',c.parent_id)
FROM wms_item_category c
WHERE c.tenant_id=@s2211_source_tenant_id AND c.deleted=b'0'
UNION ALL
SELECT 'WMS_SKU',s.id,
       JSON_OBJECT('tenantId',s.tenant_id,'name',s.name,'itemId',s.item_id)
FROM wms_item_sku s
WHERE s.tenant_id=@s2211_source_tenant_id AND s.deleted=b'0'
UNION ALL
SELECT 'WMS_INVENTORY',i.id,
       JSON_OBJECT('tenantId',i.tenant_id,'skuId',i.sku_id,
                   'warehouseId',i.warehouse_id,'areaId',i.area_id)
FROM wms_inventory i
WHERE i.tenant_id=@s2211_source_tenant_id AND i.deleted=b'0'
UNION ALL
SELECT 'WMS_WAREHOUSE',w.id,
       JSON_OBJECT('tenantId',w.tenant_id,'name',w.name)
FROM wms_warehouse w
WHERE w.tenant_id=@s2211_source_tenant_id AND w.deleted=b'0';

-- ---------------------------------------------------------------------------
-- G. 历史交易事实仅登记父级关系，绝不在本脚本修改状态、金额或物流事实。
-- 当前无记录的资源类型也会出现在最终缺口校验结果中。
-- ---------------------------------------------------------------------------
INSERT INTO tmp_s2211_tenant3000_inventory(resource_type,source_resource_id,rollback_payload)
SELECT 'TRADE_ORDER',o.id,
       JSON_OBJECT('tenantId',o.tenant_id,'shopId',o.shop_id,
                   'inquiryId',o.inquiry_id,'buyerEnterpriseId',o.buyer_enterprise_id)
FROM trade_order o
WHERE o.tenant_id=@s2211_source_tenant_id AND o.deleted=b'0'
UNION ALL
SELECT 'TRADE_ORDER_ITEM',i.id,
       JSON_OBJECT('tenantId',i.tenant_id,'orderId',i.order_id,'shopId',i.shop_id,
                   'spuId',i.spu_id,'skuId',i.sku_id)
FROM trade_order_item i
WHERE i.tenant_id=@s2211_source_tenant_id AND i.deleted=b'0'
UNION ALL
SELECT 'PAY_ORDER',p.id,
       JSON_OBJECT('tenantId',p.tenant_id,'appId',p.app_id,
                   'merchantOrderId',p.merchant_order_id)
FROM pay_order p
WHERE p.tenant_id=@s2211_source_tenant_id AND p.deleted=b'0'
UNION ALL
SELECT 'PAY_ORDER_EXTENSION',e.id,
       JSON_OBJECT('tenantId',e.tenant_id,'payOrderId',e.order_id)
FROM pay_order_extension e
WHERE e.tenant_id=@s2211_source_tenant_id AND e.deleted=b'0'
UNION ALL
SELECT 'PAY_REFUND',r.id,
       JSON_OBJECT('tenantId',r.tenant_id,'payOrderId',r.order_id)
FROM pay_refund r
WHERE r.tenant_id=@s2211_source_tenant_id AND r.deleted=b'0'
UNION ALL
SELECT 'AFTER_SALE',a.id,
       JSON_OBJECT('tenantId',a.tenant_id,'orderId',a.order_id,
                   'orderItemId',a.order_item_Id,'shopId',a.shop_id)
FROM trade_after_sale a
WHERE a.tenant_id=@s2211_source_tenant_id AND a.deleted=b'0'
UNION ALL
SELECT 'AFTER_SALE_LOG',l.id,
       JSON_OBJECT('tenantId',l.tenant_id,'afterSaleId',l.after_sale_id)
FROM trade_after_sale_log l
WHERE l.tenant_id=@s2211_source_tenant_id AND l.deleted=b'0'
UNION ALL
SELECT 'CART',c.id,
       JSON_OBJECT('tenantId',c.tenant_id,'memberId',c.user_id,
                   'spuId',c.spu_id,'skuId',c.sku_id)
FROM trade_cart c
WHERE c.tenant_id=@s2211_source_tenant_id AND c.deleted=b'0'
UNION ALL
SELECT 'PLATFORM_CART',c.id,
       JSON_OBJECT('tenantId',c.tenant_id,'platformMemberId',c.platform_member_id,
                   'enterpriseId',c.enterprise_id,'shopId',c.shop_id,
                   'spuId',c.spu_id,'skuId',c.sku_id)
FROM trade_platform_cart c
WHERE c.tenant_id=@s2211_source_tenant_id AND c.deleted=b'0'
UNION ALL
SELECT 'PLATFORM_CHECKOUT_ORDER',o.id,
       JSON_OBJECT('tenantId',o.tenant_id,'checkoutId',o.checkout_id,
                   'orderId',o.order_id,'shopId',o.shop_id)
FROM trade_platform_checkout_order o
WHERE o.tenant_id=@s2211_source_tenant_id AND o.deleted=b'0';

-- ---------------------------------------------------------------------------
-- H. 幂等写入。仅当目标仍未指定时刷新 QUARANTINED 清单；人工已确认的迁移结果不覆盖。
-- ---------------------------------------------------------------------------
INSERT INTO mall_public_tenant_migration
 (batch_no,resource_type,source_tenant_id,source_resource_id,
  migration_status,failure_reason,rollback_payload,creator,updater,deleted)
SELECT @s2211_batch_no,s.resource_type,@s2211_source_tenant_id,s.source_resource_id,
       'QUARANTINED',@s2211_reason,s.rollback_payload,
       @s2211_operator,@s2211_operator,b'0'
FROM tmp_s2211_tenant3000_inventory s
ON DUPLICATE KEY UPDATE
 migration_status=IF(target_tenant_id IS NULL AND target_resource_id IS NULL,
                     'QUARANTINED',migration_status),
 failure_reason=IF(target_tenant_id IS NULL AND target_resource_id IS NULL,
                   VALUES(failure_reason),failure_reason),
 rollback_payload=IF(target_tenant_id IS NULL AND target_resource_id IS NULL,
                     VALUES(rollback_payload),mall_public_tenant_migration.rollback_payload),
 updater=VALUES(updater),deleted=b'0';

-- ---------------------------------------------------------------------------
-- I. 部署前验证结果集与缺口计数。
--    通过条件：missing_inventory_count / unexpected_status_count / sensitive_payload_count
--             / duplicate_active_count 均为 0；second_run_added_count 由部署程序在二次执行时校验为 0。
-- ---------------------------------------------------------------------------

-- 固定口径表使当前为零的资源类型也能稳定出现在验证结果中，同时避免 MySQL
-- 在同一语句中多次打开临时源表的限制。
DROP TEMPORARY TABLE IF EXISTS tmp_s2211_expected_counts;
CREATE TEMPORARY TABLE tmp_s2211_expected_counts (
  resource_type varchar(32) NOT NULL PRIMARY KEY,
  source_count bigint NOT NULL DEFAULT 0
) ENGINE=InnoDB;
INSERT INTO tmp_s2211_expected_counts(resource_type,source_count) VALUES
 ('MEMBER',0),('MEMBER_ADDRESS',0),('MEMBER_AFFILIATION',0),
 ('PLATFORM_ACCOUNT_LINK',0),('PLATFORM_MEMBERSHIP',0),
 ('ENTERPRISE_APPLICATION',0),('ENTERPRISE',0),('ENTERPRISE_ADMIN_MEMBER',0),
 ('SHOP',0),('SHOP_ADMIN_AUTH',0),('SHOP_RETURN_ADDRESS',0),
 ('BRAND',0),('CATEGORY',0),('PROPERTY',0),('PROPERTY_VALUE',0),
 ('SPU',0),('SKU',0),('PRODUCT_COMMENT',0),('FAVORITE',0),('BROWSE_HISTORY',0),
 ('COMPARISON_LIST',0),('SPU_REVIEW',0),('STOCK_ADJUSTMENT',0),
 ('INQUIRY',0),('INQUIRY_ITEM',0),('INQUIRY_INVITED_SHOP',0),
 ('INQUIRY_INVITED_SCOPE',0),('INQUIRY_QUOTE',0),('INQUIRY_QUOTE_ITEM',0),
 ('INQUIRY_QUOTE_MESSAGE',0),('EXPRESS_TEMPLATE',0),('EXPRESS_TEMPLATE_CHARGE',0),
 ('EXPRESS_TEMPLATE_FREE',0),('KEFU_ADMIN_AGENT',0),('KEFU_MEMBER_AGENT',0),
 ('WMS_ITEM',0),('WMS_BRAND',0),('WMS_CATEGORY',0),('WMS_SKU',0),
 ('WMS_INVENTORY',0),('WMS_WAREHOUSE',0),
 ('TRADE_ORDER',0),('TRADE_ORDER_ITEM',0),('PAY_ORDER',0),
 ('PAY_ORDER_EXTENSION',0),('PAY_REFUND',0),('AFTER_SALE',0),('AFTER_SALE_LOG',0),
 ('CART',0),('PLATFORM_CART',0),('PLATFORM_CHECKOUT_ORDER',0);
INSERT INTO tmp_s2211_expected_counts(resource_type,source_count)
SELECT resource_type,COUNT(*)
FROM tmp_s2211_tenant3000_inventory
GROUP BY resource_type
ON DUPLICATE KEY UPDATE source_count=VALUES(source_count);

-- I-1. 每类来源数、已盘点数和缺口。零数据的历史交易类型也显式列出。
SELECT e.resource_type,
       e.source_count,
       COUNT(m.id) AS inventoried_count,
       e.source_count-COUNT(m.id) AS missing_inventory_count
FROM tmp_s2211_expected_counts e
LEFT JOIN mall_public_tenant_migration m
  ON m.batch_no=@s2211_batch_no
 AND m.resource_type=e.resource_type
 AND m.source_tenant_id=@s2211_source_tenant_id
 AND m.deleted=b'0'
GROUP BY e.resource_type,e.source_count
ORDER BY e.resource_type;

-- I-2. 汇总缺口与异常状态。四个异常计数均应为 0。
SELECT
  (SELECT SUM(source_count) FROM tmp_s2211_expected_counts) AS source_resource_count,
  (SELECT COUNT(*) FROM mall_public_tenant_migration m
    WHERE m.batch_no=@s2211_batch_no AND m.source_tenant_id=@s2211_source_tenant_id
      AND m.deleted=b'0') AS inventory_resource_count,
  (SELECT COUNT(*)
     FROM tmp_s2211_tenant3000_inventory s
     LEFT JOIN mall_public_tenant_migration m
       ON m.batch_no=@s2211_batch_no AND m.resource_type=s.resource_type
      AND m.source_tenant_id=@s2211_source_tenant_id
      AND m.source_resource_id=s.source_resource_id AND m.deleted=b'0'
    WHERE m.id IS NULL) AS missing_inventory_count,
  (SELECT COUNT(*) FROM mall_public_tenant_migration m
    WHERE m.batch_no=@s2211_batch_no AND m.source_tenant_id=@s2211_source_tenant_id
      AND m.target_tenant_id IS NULL AND m.target_resource_id IS NULL
      AND m.migration_status<>'QUARANTINED' AND m.deleted=b'0') AS unexpected_status_count,
  (SELECT COUNT(*) FROM mall_public_tenant_migration m
    WHERE m.batch_no=@s2211_batch_no AND m.source_tenant_id=@s2211_source_tenant_id
      AND m.deleted=b'0'
      AND (LOWER(CAST(m.rollback_payload AS CHAR)) LIKE '%"password"%'
        OR LOWER(CAST(m.rollback_payload AS CHAR)) LIKE '%"token"%'
        OR LOWER(CAST(m.rollback_payload AS CHAR)) LIKE '%"mobile"%'
        OR LOWER(CAST(m.rollback_payload AS CHAR)) LIKE '%"phone"%'
        OR LOWER(CAST(m.rollback_payload AS CHAR)) LIKE '%"contactmobile"%'
        OR LOWER(CAST(m.rollback_payload AS CHAR)) LIKE '%"loginname"%'
        OR LOWER(CAST(m.rollback_payload AS CHAR)) LIKE '%"credentialidentifier"%'
        OR LOWER(CAST(m.rollback_payload AS CHAR)) LIKE '%"creditcode"%')) AS sensitive_payload_count,
  (SELECT COUNT(*) FROM (
     SELECT resource_type,source_resource_id,COUNT(*) AS row_count
     FROM mall_public_tenant_migration
     WHERE batch_no=@s2211_batch_no AND source_tenant_id=@s2211_source_tenant_id AND deleted=b'0'
     GROUP BY resource_type,source_resource_id HAVING COUNT(*)>1
   ) d) AS duplicate_active_count;

-- I-3. 仍引用租户 3000 的平台账号链接必须保持隔离态，READY 视为阻断项。
SELECT l.link_status,COUNT(*) AS link_count
FROM member_platform_account_link l
WHERE l.source_tenant_id=@s2211_source_tenant_id AND l.deleted=b'0'
GROUP BY l.link_status ORDER BY l.link_status;
SELECT COUNT(*) AS tenant3000_ready_platform_link_count
FROM member_platform_account_link l
WHERE l.source_tenant_id=@s2211_source_tenant_id
  AND l.link_status='READY' AND l.deleted=b'0';

-- I-4. 人工确认目标后的记录单独展示；本脚本不会覆盖这些记录。
SELECT resource_type,source_resource_id,target_tenant_id,target_resource_id,
       migration_status,verified_by,verified_at
FROM mall_public_tenant_migration
WHERE batch_no=@s2211_batch_no AND source_tenant_id=@s2211_source_tenant_id
  AND (target_tenant_id IS NOT NULL OR target_resource_id IS NOT NULL)
  AND deleted=b'0'
ORDER BY resource_type,source_resource_id;

DROP TEMPORARY TABLE IF EXISTS tmp_s2211_tenant3000_inventory;
DROP TEMPORARY TABLE IF EXISTS tmp_s2211_expected_counts;
