-- =====================================================================
-- 2026-09-16 报表数据集支持「业务分组」多归属
-- 背景：报表中心左侧目录按业务分组（生产/品质/设备/库存/自定义等）展示，
--       分组由页面自由维护，新增分组无需改代码。
-- 新增：report_dataset.biz_groups 业务分组，逗号分隔（如「生产,库存」）。
-- 说明：biz_groups 字段已在 ReportDatasetDO 中定义，但此前遗漏了数据库迁移，
--       导致服务启动后访问报表中心报「Unknown column 'biz_groups'」。
--       本次补充迁移；MySQL 8 会因列已存在而报错，属预期（幂等靠人工判断）。
-- =====================================================================

SET NAMES utf8mb4;

ALTER TABLE `report_dataset`
    ADD COLUMN `biz_groups` varchar(255) NOT NULL DEFAULT '' COMMENT '业务分组，逗号分隔（如：生产,库存）；报表中心目录按此分组' AFTER `category`;
