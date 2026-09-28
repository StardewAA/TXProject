USE `ruoyi-vue-pro`;

-- ============================================================================
-- 009 公开资源口径重算（2026-09-24）
--
-- 背景（③ 复评问题 3）：
--   006 的 B2b 曾按 `path LIKE 'mall/product/%'` 等**路径前缀**批量公开文件。但历史
--   directory 原本是客户端传入的，前缀不等于"业务上确实公开"，不符合既定口径
--   「依据真实业务记录确认公开」。
--
-- 本脚本把公开口径**收敛为唯一证据源**：一个文件只有被"公开面业务数据"实际引用，
-- 才确认为公开（access_type=1 / bind_status=2）；否则回落私有（默认姿态）。
--
-- 2026-09-25：收集所有非空业务图片引用，同时覆盖网关 URL 和 OSS HTTPS 直链；
-- 不能用 /infra/file/ 前缀预过滤，否则空库的 35 张 OSS 演示图会被误标为私有。
-- 引用识别方式：把公开面各列的值（含 JSON 数组、富文本 HTML）汇入临时表，
-- 再以「文件 path 作为子串」匹配；path 是 URL 中不变的部分（网关 /infra/file/{cfg}/get/{path}
-- 与 OSS 直连 {domain}/{path} 均含 path），因此提升到 OSS 后重算也不会误回落私有。
-- path 中的 LIKE 通配符（_ %）已转义（ESCAPE '!'）。
--
-- 同时做两件事：
--   A. 历史 owner_user_type 回填（仅当编号在 member_user / system_users 中**唯一可判**时）；
--   B. 被服务端 RPC 绑定过（biz_id 非空）的文件不参与重算，避免覆盖业务绑定结果。
--
-- 维护：公开面列清单由 script/discover-url-columns.py 全库扫描得出；
--      新增对外展示的图片列时，必须同步补充本脚本 §1。
-- ============================================================================

SET NAMES utf8mb4;

-- ------------------------- A. 历史上传者用户类型回填 -------------------------
-- 历史行只回填了 owner_user_id（来自 creator），owner_user_type 为空会导致
-- isOwner 严格匹配失效。仅在"编号只出现在一侧"时可判定，避免会员/后台同号误判。
UPDATE `infra_file` f
SET f.`owner_user_type` = 2
WHERE f.`owner_user_type` IS NULL
  AND f.`owner_user_id` IS NOT NULL
  AND EXISTS (SELECT 1 FROM `system_users` u WHERE u.`id` = f.`owner_user_id`)
  AND NOT EXISTS (SELECT 1 FROM `member_user` m WHERE m.`id` = f.`owner_user_id`);

UPDATE `infra_file` f
SET f.`owner_user_type` = 1
WHERE f.`owner_user_type` IS NULL
  AND f.`owner_user_id` IS NOT NULL
  AND EXISTS (SELECT 1 FROM `member_user` m WHERE m.`id` = f.`owner_user_id`)
  AND NOT EXISTS (SELECT 1 FROM `system_users` u WHERE u.`id` = f.`owner_user_id`);

-- ---------------- 1. 汇集「公开面业务数据引用」的原始单元格 ------------------
DROP TEMPORARY TABLE IF EXISTS `tmp_public_ref`;
CREATE TEMPORARY TABLE `tmp_public_ref`
(
    `v` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
);

INSERT INTO `tmp_public_ref` (`v`)
-- 商品
SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_spu` WHERE NULLIF(`pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`slider_pic_urls` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_spu` WHERE NULLIF(`slider_pic_urls`, '') IS NOT NULL
UNION SELECT CONVERT(`description` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_spu` WHERE NULLIF(`description`, '') IS NOT NULL
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_sku` WHERE NULLIF(`pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_brand` WHERE NULLIF(`pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_category` WHERE NULLIF(`pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`logo_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_shop` WHERE NULLIF(`logo_url`, '') IS NOT NULL
-- 评价（前台公开展示）
UNION SELECT CONVERT(`pic_urls` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_comment` WHERE NULLIF(`pic_urls`, '') IS NOT NULL
UNION SELECT CONVERT(`sku_pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_comment` WHERE NULLIF(`sku_pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`user_avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `product_comment` WHERE NULLIF(`user_avatar`, '') IS NOT NULL
-- 运营 / 装修
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_banner` WHERE NULLIF(`pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_article` WHERE NULLIF(`pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_article_category` WHERE NULLIF(`pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`slider_pic_urls` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_seckill_config` WHERE NULLIF(`slider_pic_urls`, '') IS NOT NULL
UNION SELECT CONVERT(`preview_pic_urls` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_diy_page` WHERE NULLIF(`preview_pic_urls`, '') IS NOT NULL
UNION SELECT CONVERT(`preview_pic_urls` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_diy_template` WHERE NULLIF(`preview_pic_urls`, '') IS NOT NULL
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_combination_record` WHERE NULLIF(`pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `promotion_combination_record` WHERE NULLIF(`avatar`, '') IS NOT NULL
-- 会员等级
UNION SELECT CONVERT(`background_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `member_level` WHERE NULLIF(`background_url`, '') IS NOT NULL
UNION SELECT CONVERT(`icon` USING utf8mb4) COLLATE utf8mb4_bin FROM `member_level` WHERE NULLIF(`icon`, '') IS NOT NULL
-- 交易（前台展示：订单项商品图、店铺 logo、配送 logo、结算项图）
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_order_item` WHERE NULLIF(`pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`shop_logo_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_order` WHERE NULLIF(`shop_logo_url`, '') IS NOT NULL
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_after_sale` WHERE NULLIF(`pic_url`, '') IS NOT NULL
UNION SELECT CONVERT(`logo` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_delivery_express` WHERE NULLIF(`logo`, '') IS NOT NULL
UNION SELECT CONVERT(`logo` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_delivery_pick_up_store` WHERE NULLIF(`logo`, '') IS NOT NULL
UNION SELECT CONVERT(`pic_url` USING utf8mb4) COLLATE utf8mb4_bin FROM `trade_platform_checkout_plan_item` WHERE NULLIF(`pic_url`, '') IS NOT NULL
-- 头像（评论区/后台均对外可见，且 <img> 无法携带登录态）
UNION SELECT CONVERT(`avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `member_user` WHERE NULLIF(`avatar`, '') IS NOT NULL
UNION SELECT CONVERT(`avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `system_users` WHERE NULLIF(`avatar`, '') IS NOT NULL;

-- 可选表：`ai_chat_role` / `txgy_demo01_contact` 不在本目录 001/002 初始化脚本内，
-- 全新库上不存在，直接 UNION 会报 1146。这里用临时存储过程按 information_schema 判断，
-- 表存在才计入公开面（口径不变：这两张表的 avatar 同样出现在对外页面）。
DROP PROCEDURE IF EXISTS `tmp_009_collect_optional_refs`;
DELIMITER $$
CREATE PROCEDURE `tmp_009_collect_optional_refs`()
BEGIN
    IF (SELECT COUNT(*) FROM `information_schema`.`tables`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'ai_chat_role') = 1 THEN
        INSERT INTO `tmp_public_ref` (`v`)
        SELECT CONVERT(`avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `ai_chat_role`
        WHERE NULLIF(`avatar`, '') IS NOT NULL;
    END IF;
    IF (SELECT COUNT(*) FROM `information_schema`.`tables`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'txgy_demo01_contact') = 1 THEN
        INSERT INTO `tmp_public_ref` (`v`)
        SELECT CONVERT(`avatar` USING utf8mb4) COLLATE utf8mb4_bin FROM `txgy_demo01_contact`
        WHERE NULLIF(`avatar`, '') IS NOT NULL;
    END IF;
END$$
DELIMITER ;
CALL `tmp_009_collect_optional_refs`();
DROP PROCEDURE `tmp_009_collect_optional_refs`;

-- ---------------- 2. 确认公开：被公开面业务数据实际引用 ----------------------
UPDATE `infra_file` f
SET f.`access_type` = 1,
    f.`bind_status` = 2,
    f.`bind_expire_time` = NULL
WHERE f.`deleted` = 0
  AND NULLIF(f.`path`, '') IS NOT NULL
  AND EXISTS (SELECT 1
              FROM `tmp_public_ref` t
              WHERE t.`v` LIKE CONCAT('%',
                                      REPLACE(REPLACE(REPLACE(f.`path`, '!', '!!'), '_', '!_'), '%', '!%'),
                                      '%') ESCAPE '!');

-- ---------------- 3. 回落私有：未被任何公开面数据引用 ------------------------
-- 仅收敛"曾是公开、且未经服务端 RPC 绑定"的记录（biz_id 为空），避免覆盖业务绑定结果。
UPDATE `infra_file` f
SET f.`access_type` = 2,
    f.`bind_status` = 0,
    f.`bind_expire_time` = NULL
WHERE f.`deleted` = 0
  AND f.`access_type` = 1
  AND f.`biz_id` IS NULL
  AND NOT EXISTS (SELECT 1
                  FROM `tmp_public_ref` t
                  WHERE t.`v` LIKE CONCAT('%',
                                          REPLACE(REPLACE(REPLACE(f.`path`, '!', '!!'), '_', '!_'), '%', '!%'),
                                          '%') ESCAPE '!');

DROP TEMPORARY TABLE IF EXISTS `tmp_public_ref`;

-- ---------------- 4. 复核输出 ----------------------
SELECT '存活总数' AS k, COUNT(*) AS v FROM `infra_file` WHERE `deleted` = 0
UNION ALL SELECT '公开(access_type=1)', COUNT(*) FROM `infra_file` WHERE `deleted` = 0 AND `access_type` = 1
UNION ALL SELECT '私有(access_type=2)', COUNT(*) FROM `infra_file` WHERE `deleted` = 0 AND `access_type` = 2
UNION ALL SELECT 'owner_user_type 已判定', COUNT(*) FROM `infra_file` WHERE `deleted` = 0 AND `owner_user_type` IS NOT NULL
UNION ALL SELECT 'owner_user_type 仍未知', COUNT(*) FROM `infra_file` WHERE `deleted` = 0 AND `owner_user_id` IS NOT NULL AND `owner_user_type` IS NULL;
