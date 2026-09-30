-- ----------------------------------------------------------------------------
-- 2026-09-22 标书模板与变量字典落地方案（改进版）P2：计价表数据通道（PRICING 材料角色）
--   1. 材料台账新增项目归属 project_id（PRICING 报价数据必填；其余角色可空）
--   2. 新增报价数据结构化表 bidding_bid_pricing：报价 Excel 解析后按「表名 + 行号 + 列值 JSON」落库，
--      Node 侧 COPY_PRICING 通道经契约接口 /bidding/bid-agent/contract/pricing 拉取
-- ----------------------------------------------------------------------------

ALTER TABLE `bidding_bid_material`
    ADD COLUMN `project_id` bigint DEFAULT NULL COMMENT '所属项目（PRICING 报价数据用；空=默认 0，同一键生成口径，其余角色可空）' AFTER `bid_section_code`;

CREATE TABLE IF NOT EXISTS `bidding_bid_pricing` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `material_id` bigint NOT NULL COMMENT '来源材料编号（bidding_bid_material.id，按材料精确级联清理/重建）',
    `project_id` bigint NOT NULL COMMENT '所属项目',
    `table_name` varchar(32) NOT NULL COMMENT '计价表名：bill/measure/other/daywork/service/fee/analysis/material/project_cost/unit_cost/cost_breakdown/shared',
    `row_no` int NOT NULL DEFAULT '0' COMMENT '数据行号（从 1 起；shared 表为变量行号）',
    `data_json` text COMMENT '该行列值 JSON：{列名: 文本值}',
    `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
    `creator` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_project_table` (`tenant_id`, `project_id`, `table_name`),
    KEY `idx_material` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标书智能体-报价数据（PRICING 材料角色，COPY_PRICING 通道数据源）';
