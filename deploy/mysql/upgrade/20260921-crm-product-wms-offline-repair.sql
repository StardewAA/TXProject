-- =====================================================================
-- 历史 WMS 同步产品下架修复（配合 CRM 产品实时只读方案）
-- 日期：2026-09-21
-- 背景：CRM 产品管理主流程已改为实时读取 WMS，不再落库；
--       但历史上由同步写入 crm_product 的记录仍被商机/合同/报价引用，
--       WMS 侧已删除或停用的规格需要置为「下架」（status=0），避免残留上架状态。
-- 说明：多租户表，请按租户逐个执行；执行前先用下方 SELECT 核对影响行数。
-- =====================================================================

SET NAMES utf8mb4;

-- 核对影响行数
SELECT COUNT(*) AS will_offline
FROM `crm_product` p
WHERE p.`deleted` = 0
  AND p.`data_source` = 2
  AND (p.`wms_sku_id` IS NULL
       OR NOT EXISTS (SELECT 1 FROM `wms_item_sku` s
                      WHERE s.`id` = p.`wms_sku_id` AND s.`deleted` = 0));

-- 置为下架（DISABLE = 0）
UPDATE `crm_product` p
SET p.`status` = 0,
    p.`update_time` = NOW()
WHERE p.`deleted` = 0
  AND p.`data_source` = 2
  AND (p.`wms_sku_id` IS NULL
       OR NOT EXISTS (SELECT 1 FROM `wms_item_sku` s
                      WHERE s.`id` = p.`wms_sku_id` AND s.`deleted` = 0));
