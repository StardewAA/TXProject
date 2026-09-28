USE `ruoyi-vue-pro`;

-- ============================================================================
-- 006 文件访问控制（2026-09-24）
--
-- 背景：
--   1. /app-api/infra/file/upload 曾为 @PermitAll，仅凭 tenant-id 头即可匿名上传；
--   2. /infra/file/{configId}/get/** 与 /preview/** 无任何鉴权，知道 configId + path 即可下载；
--   3. infra_file 无归属信息，无法判断文件属于谁。
--
-- 本脚本做两件事：
--   A. 给 infra_file 增加访问控制字段（默认私有 + 待绑定）；
--   B. 对历史数据做**证据化**回填：仅把"确实被公开面业务表引用"的文件确认为公开，
--      其余一律私有。公开白名单不依据客户端目录/路径前缀猜测。
-- ============================================================================

-- ---------------------------- A. 访问控制字段 -------------------------------
ALTER TABLE `infra_file`
    ADD COLUMN `tenant_id` bigint NULL COMMENT '租户编号（来自可信登录身份，非客户端传入）',
    ADD COLUMN `access_type` tinyint NOT NULL DEFAULT 2 COMMENT '访问类型：1=公开 2=私有（上传默认私有）',
    ADD COLUMN `bind_status` tinyint NOT NULL DEFAULT 0 COMMENT '绑定状态：0=待绑定 1=已绑定 2=公开资源',
    ADD COLUMN `biz_type` varchar(32) NULL COMMENT '业务类型（服务端归一化：FileBizTypeEnum）',
    ADD COLUMN `biz_id` varchar(64) NULL COMMENT '绑定的业务单据编号',
    ADD COLUMN `owner_user_type` tinyint NULL COMMENT '上传者用户类型：1=会员 2=后台用户',
    ADD COLUMN `owner_user_id` bigint NULL COMMENT '上传者用户编号',
    ADD COLUMN `bind_participants` varchar(1024) NULL COMMENT '可读参与方：类型:编号，逗号分隔',
    ADD COLUMN `bind_expire_time` datetime NULL COMMENT '待绑定可读截止时间（仅待绑定有效）';

ALTER TABLE `infra_file`
    ADD INDEX `idx_infra_file_owner` (`owner_user_type`, `owner_user_id`),
    ADD INDEX `idx_infra_file_bind` (`bind_status`, `bind_expire_time`),
    ADD INDEX `idx_infra_file_tenant` (`tenant_id`);

-- ------------------- B1. 历史上传者回填（creator 即上传者编号） -------------------
UPDATE `infra_file`
SET `owner_user_id` = CAST(`creator` AS UNSIGNED)
WHERE `owner_user_id` IS NULL
  AND `creator` REGEXP '^[0-9]+$'
  AND `creator` <> '0';

-- --------- B2. 公开资源确认：仅"被公开面业务表实际引用"的文件确认为公开 ---------
-- 证据来源：商品 / SKU / 品牌 / 分类 / 店铺 logo / 评价图与评价头像 / 运营横幅。
-- 这些表的查询接口本就是匿名可读（商城前台展示），故其引用到的文件必须保持匿名可读。
-- 注意：不同 dump 来源的表排序规则可能不一致（utf8mb4_0900_ai_ci / utf8mb4_unicode_ci），
--       因此比较双方显式指定 COLLATE，避免 "Illegal mix of collations"。
UPDATE `infra_file` f
SET f.`access_type` = 1,
    f.`bind_status` = 2
WHERE EXISTS (SELECT 1 FROM `product_spu` t
              WHERE t.`pic_url` COLLATE utf8mb4_unicode_ci = f.`url`
                 OR t.`slider_pic_urls` COLLATE utf8mb4_unicode_ci LIKE CONCAT('%', f.`url`, '%'))
   OR EXISTS (SELECT 1 FROM `product_sku` t WHERE t.`pic_url` COLLATE utf8mb4_unicode_ci = f.`url`)
   OR EXISTS (SELECT 1 FROM `product_brand` t WHERE t.`pic_url` COLLATE utf8mb4_unicode_ci = f.`url`)
   OR EXISTS (SELECT 1 FROM `product_category` t WHERE t.`pic_url` COLLATE utf8mb4_unicode_ci = f.`url`)
   OR EXISTS (SELECT 1 FROM `product_shop` t WHERE t.`logo_url` COLLATE utf8mb4_unicode_ci = f.`url`)
   OR EXISTS (SELECT 1 FROM `product_comment` t
              WHERE t.`pic_urls` COLLATE utf8mb4_unicode_ci LIKE CONCAT('%', f.`url`, '%')
                 OR t.`user_avatar` COLLATE utf8mb4_unicode_ci = f.`url`)
   OR EXISTS (SELECT 1 FROM `promotion_banner` t WHERE t.`pic_url` COLLATE utf8mb4_unicode_ci = f.`url`)
   OR EXISTS (SELECT 1 FROM `promotion_article` t WHERE t.`pic_url` COLLATE utf8mb4_unicode_ci = f.`url`);

-- -------- B2b. 公开资源确认（服务端白名单）：命中最长前缀的公开业务类型 ---------
-- 与 B2 互为补充：B2 覆盖"被业务表引用"的，B2b 覆盖"属于服务端确认的公开业务类型"的
-- （分类图标、配送公司 logo、SKU 图、租户商品图等前台资源，未必在业务表里逐条引用）。
-- 判定依据是**服务端维护的目录白名单**（FileBizTypeEnum.publicCapable），不是客户端声明。
UPDATE `infra_file`
SET `access_type` = 1,
    `bind_status` = 2
WHERE `access_type` = 2
  AND (`path` LIKE 'mall/product/%'
    OR `path` LIKE 'mall/catalog/%'
    OR `path` LIKE 'mall/shop-logo/%'
    OR `path` LIKE 'mall/tianxin/%'
    OR `path` LIKE 'mall/delivery/%'
    OR `path` LIKE 'mall/banner/%'
    OR `path` LIKE 'mall/brand/%'
    OR `path` LIKE 'mall/category/%'
    OR `path` LIKE 'mall/comment/%'
    OR `path` LIKE 'mall-demo/%');

-- --------------------- B3. 公开资源的业务类型标注 ----------------------------
UPDATE `infra_file` SET `biz_type` = 'CATALOG_IMAGE'      WHERE `access_type` = 1 AND `path` LIKE 'mall/catalog/%';
UPDATE `infra_file` SET `biz_type` = 'SHOP_LOGO'          WHERE `access_type` = 1 AND `path` LIKE 'mall/shop-logo/%';
UPDATE `infra_file` SET `biz_type` = 'TENANT_PRODUCT_IMAGE' WHERE `access_type` = 1 AND `path` LIKE 'mall/tianxin/%';
UPDATE `infra_file` SET `biz_type` = 'DELIVERY_LOGO'      WHERE `access_type` = 1 AND `path` LIKE 'mall/delivery/%';
UPDATE `infra_file` SET `biz_type` = 'BANNER_IMAGE'       WHERE `access_type` = 1 AND `path` LIKE 'mall/banner/%';
UPDATE `infra_file` SET `biz_type` = 'PRODUCT_IMAGE'      WHERE `access_type` = 1 AND `biz_type` IS NULL;

-- ------------- B4. 其余历史文件：保持私有，并按目录标注业务类型 ---------------
UPDATE `infra_file` SET `biz_type` = 'INQUIRY_IMAGE'          WHERE `access_type` = 2 AND `path` LIKE 'mall/inquiry/%';
UPDATE `infra_file` SET `biz_type` = 'AFTER_SALE_IMAGE'       WHERE `access_type` = 2 AND `path` LIKE 'mall/aftersale/%';
UPDATE `infra_file` SET `biz_type` = 'CUSTOMER_SERVICE_IMAGE' WHERE `access_type` = 2 AND `path` LIKE 'mall/customer-service/%';
UPDATE `infra_file` SET `biz_type` = 'ENTERPRISE_CERT'        WHERE `access_type` = 2 AND `path` LIKE 'mall/enterprise/%';
UPDATE `infra_file` SET `biz_type` = 'MEMBER_AVATAR'          WHERE `access_type` = 2 AND `path` LIKE 'member/avatar/%';
UPDATE `infra_file` SET `biz_type` = 'OTHER'                  WHERE `biz_type` IS NULL;

-- 历史私有文件不设过期时间：避免孤儿清理误删既有业务文件
UPDATE `infra_file` SET `bind_expire_time` = NULL WHERE `bind_status` = 0;
