-- =====================================================================
-- 受控存储（需求 5.4）：库位污染管控属性 + 判定/台账落库位编号
-- 2026-09-10
--
-- 设计：不自建库位字典，复用既有 wm 主数据链 仓库(mes_wm_warehouse)
--       → 库区(mes_wm_warehouse_location) → 库位(mes_wm_warehouse_area)。
--       库位表已有 frozen/status/allow_item_mixing 等存放规则列，
--       「污染管控/危废」属性加在同一处最顺。
--       location 文本保留为名称快照（存量行不动），新增 location_id 做门禁与关联。
-- =====================================================================

-- 1. 库位：污染管控标识（1=受控/危废库位，普通物料不得入内；0=普通库位）
ALTER TABLE `mes_wm_warehouse_area`
    ADD COLUMN `pollution_control` tinyint(1) NOT NULL DEFAULT 0
        COMMENT '是否污染管控库位（1=受控/危废暂存，污染品只能入此处，普通品不得占用）'
        AFTER `allow_batch_mixing`;

-- 2. 污染判定记录：所选受控库位编号
ALTER TABLE `mes_set_pollution_check`
    ADD COLUMN `location_id` bigint NULL
        COMMENT '受控库位编号（mes_wm_warehouse_area.id；location 为其名称快照）'
        AFTER `location`;

-- 3. 污染台账：同上（复核登记台账时从判定带过来）
ALTER TABLE `mes_pollution_ledger`
    ADD COLUMN `location_id` bigint NULL
        COMMENT '受控库位编号（mes_wm_warehouse_area.id；location 为其名称快照）'
        AFTER `location`;


-- =====================================================================
-- 4. 租户 2010（华瀚）库位主数据种子
--    该租户此前无任何 仓库/库区/库位 数据；库位名沿用演示已用的暂存间名，
--    便于存量台账行按名称回填 location_id。
-- =====================================================================

INSERT INTO `mes_wm_warehouse` (`code`, `name`, `address`, `frozen`, `remark`, `creator`, `tenant_id`)
VALUES ('WH-HZ-RAW', '原料仓', '华瀚厂区', 0, '污染监控演示用仓库', 'system', 2010),
       ('WH-HZ-HAZ', '危废暂存仓', '华瀚厂区东侧', 0, '受控仓：与普通仓物理隔离', 'system', 2010);

INSERT INTO `mes_wm_warehouse_location` (`code`, `name`, `warehouse_id`, `frozen`, `remark`, `creator`, `tenant_id`)
SELECT 'LOC-HZ-RAW-A', '原料 A 区', w.id, 0, '普通库区', 'system', 2010 FROM `mes_wm_warehouse` w
WHERE w.`code` = 'WH-HZ-RAW' AND w.`tenant_id` = 2010 AND w.`deleted` = 0;
INSERT INTO `mes_wm_warehouse_location` (`code`, `name`, `warehouse_id`, `frozen`, `remark`, `creator`, `tenant_id`)
SELECT 'LOC-HZ-HAZ', '危废暂存区', w.id, 0, '受控库区：仅收污染/危废品', 'system', 2010 FROM `mes_wm_warehouse` w
WHERE w.`code` = 'WH-HZ-HAZ' AND w.`tenant_id` = 2010 AND w.`deleted` = 0;

-- 普通库位（不受控）
INSERT INTO `mes_wm_warehouse_area` (`code`, `name`, `location_id`, `status`, `frozen`, `allow_item_mixing`, `allow_batch_mixing`, `pollution_control`, `remark`, `creator`, `tenant_id`)
SELECT 'AREA-HZ-RAW-A-01', '原料A-01', l.id, 0, 0, 1, 1, 0, '普通库位', 'system', 2010
FROM `mes_wm_warehouse_location` l WHERE l.`code` = 'LOC-HZ-RAW-A' AND l.`tenant_id` = 2010 AND l.`deleted` = 0;

-- 受控库位（污染管控，名与演示存量台账/判定的 location 文本一致）
INSERT INTO `mes_wm_warehouse_area` (`code`, `name`, `location_id`, `status`, `frozen`, `allow_item_mixing`, `allow_batch_mixing`, `pollution_control`, `remark`, `creator`, `tenant_id`)
SELECT 'AREA-HZ-HAZ-01', '危废暂存间A-01', l.id, 0, 0, 0, 0, 1, '受控库位：危废暂存', 'system', 2010
FROM `mes_wm_warehouse_location` l WHERE l.`code` = 'LOC-HZ-HAZ' AND l.`tenant_id` = 2010 AND l.`deleted` = 0;
INSERT INTO `mes_wm_warehouse_area` (`code`, `name`, `location_id`, `status`, `frozen`, `allow_item_mixing`, `allow_batch_mixing`, `pollution_control`, `remark`, `creator`, `tenant_id`)
SELECT 'AREA-HZ-HAZ-02', '危废暂存间A-02', l.id, 0, 0, 0, 0, 1, '受控库位：危废暂存', 'system', 2010
FROM `mes_wm_warehouse_location` l WHERE l.`code` = 'LOC-HZ-HAZ' AND l.`tenant_id` = 2010 AND l.`deleted` = 0;
INSERT INTO `mes_wm_warehouse_area` (`code`, `name`, `location_id`, `status`, `frozen`, `allow_item_mixing`, `allow_batch_mixing`, `pollution_control`, `remark`, `creator`, `tenant_id`)
SELECT 'AREA-HZ-HAZ-03', '危废暂存间C-03', l.id, 0, 0, 0, 0, 1, '受控库位：危废暂存', 'system', 2010
FROM `mes_wm_warehouse_location` l WHERE l.`code` = 'LOC-HZ-HAZ' AND l.`tenant_id` = 2010 AND l.`deleted` = 0;

-- 租户 1（冒烟用）：把既有成品库位标一个受控，便于跨租户验证
UPDATE `mes_wm_warehouse_area` SET `pollution_control` = 1
WHERE `tenant_id` = 1 AND `code` = 'AREA-FIN-A-01' AND `deleted` = 0;

-- =====================================================================
-- 5. 存量演示行按 location 名称回填 location_id（仅限租户 2010）
-- =====================================================================
-- 注意：判定/台账的 location 列为 utf8mb4_0900_ai_ci，库位主数据为 utf8mb4_unicode_ci，
--       JOIN 需显式 COLLATE，否则报 Illegal mix of collations。
UPDATE `mes_set_pollution_check` c
JOIN `mes_wm_warehouse_area` a ON a.`name` COLLATE utf8mb4_0900_ai_ci = c.`location`
    AND a.`tenant_id` = c.`tenant_id` AND a.`deleted` = 0
SET c.`location_id` = a.`id`
WHERE c.`tenant_id` = 2010 AND c.`deleted` = 0 AND c.`location_id` IS NULL;

UPDATE `mes_pollution_ledger` g
JOIN `mes_wm_warehouse_area` a ON a.`name` COLLATE utf8mb4_0900_ai_ci = g.`location`
    AND a.`tenant_id` = g.`tenant_id` AND a.`deleted` = 0
SET g.`location_id` = a.`id`
WHERE g.`tenant_id` = 2010 AND g.`deleted` = 0 AND g.`location_id` IS NULL;
