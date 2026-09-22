SET NAMES utf8mb4;
ALTER TABLE `mes_wm_warehouse_area` ADD COLUMN `pollution_control` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否污染管控库位（1=受控/危废暂存）', ADD COLUMN `storage_zone` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GENERAL' COMMENT '储存专区：GENERAL 一般区 / EXPLOSION_PROOF 防爆区 / ISOLATION 隔离区';
