-- mes_set_* 列注释 GBK 双重编码修复（2026-09-10）
-- 症状：注释存成 "缂栧彿"(= "编号" 的 UTF-8 字节被误按 GBK 解码)，
--       7 张表共 61 列受影响；客户端看表结构全是乱码，且按注释生成代码会把乱码带进 UI。
-- 修法：encode('gbk') -> decode('utf-8') 还原，用 INFORMATION_SCHEMA 重建整列定义 MODIFY。
-- 幂等：由 codegen/gen_fix_comments.py 生成，重复执行前先重跑生成器（已修好的列不会再产出语句）。

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `id` bigint
        NOT NULL
        AUTO_INCREMENT
        COMMENT '编号';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `record_no` varchar(64)
        NOT NULL
        COMMENT '记录编号';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `storage_location` varchar(200)
        NULL
        COMMENT '存储地点';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `remark` varchar(500)
        NULL
        COMMENT '备注';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `create_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `update_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `tenant_id` bigint
        NOT NULL
        DEFAULT '0'
        COMMENT '租户编号';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `id` bigint
        NOT NULL
        AUTO_INCREMENT
        COMMENT '编号';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `record_no` varchar(64)
        NOT NULL
        COMMENT '记录编号';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `location` varchar(200)
        NULL
        COMMENT '区域/位置';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `facility_code` varchar(64)
        NULL
        COMMENT '设施编号(资产编号)';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `remark` varchar(500)
        NULL
        COMMENT '备注';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `create_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `update_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `tenant_id` bigint
        NOT NULL
        DEFAULT '0'
        COMMENT '租户编号';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `id` bigint
        NOT NULL
        AUTO_INCREMENT
        COMMENT '编号';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `record_no` varchar(64)
        NOT NULL
        COMMENT '记录编号';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `operation_id` bigint
        NULL
        COMMENT '关联工序编号';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `permit_id` bigint
        NULL
        COMMENT '关联作业许可编号';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `remark` varchar(500)
        NULL
        COMMENT '备注';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `create_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `update_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `tenant_id` bigint
        NOT NULL
        DEFAULT '0'
        COMMENT '租户编号';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `id` bigint
        NOT NULL
        AUTO_INCREMENT
        COMMENT '编号';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `waste_name` varchar(200)
        NOT NULL
        COMMENT '危废名称';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `quantity` decimal(12,3)
        NULL
        COMMENT '数量';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `wo_id` bigint
        NULL
        COMMENT '来源工单编号';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `handle_time` datetime
        NULL
        COMMENT '交接/处理时间';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `remark` varchar(500)
        NULL
        COMMENT '备注';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `create_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `update_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `tenant_id` bigint
        NOT NULL
        DEFAULT '0'
        COMMENT '租户编号';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `id` bigint
        NOT NULL
        AUTO_INCREMENT
        COMMENT '编号';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `plan_no` varchar(64)
        NOT NULL
        COMMENT '计划编号';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `plan_name` varchar(200)
        NOT NULL
        COMMENT '计划名称';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `end_date` date
        NULL
        COMMENT '生效结束日期';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `remark` varchar(500)
        NULL
        COMMENT '备注';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `create_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `update_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `tenant_id` bigint
        NOT NULL
        DEFAULT '0'
        COMMENT '租户编号';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `id` bigint
        NOT NULL
        AUTO_INCREMENT
        COMMENT '编号';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `record_no` varchar(64)
        NOT NULL
        COMMENT '记录编号(PC-yyyyMMddHHmmss+随机)';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `item_code` varchar(64)
        NULL
        COMMENT '物料/产品编码';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `item_spec` varchar(200)
        NULL
        COMMENT '规格';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `ai_reason` varchar(1000)
        NULL
        COMMENT 'AI 判定依据';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `suggested_storage` varchar(200)
        NULL
        COMMENT 'AI 推荐存储方法';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `review_time` datetime
        NULL
        COMMENT '复核时间';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `location` varchar(200)
        NULL
        COMMENT '去向/库位';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `remark` varchar(500)
        NULL
        COMMENT '备注';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `create_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `update_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `tenant_id` bigint
        NOT NULL
        DEFAULT '0'
        COMMENT '租户编号';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `id` bigint
        NOT NULL
        AUTO_INCREMENT
        COMMENT '编号';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `standard_no` varchar(64)
        NOT NULL
        COMMENT '标准编号';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `standard_name` varchar(200)
        NOT NULL
        COMMENT '标准名称';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `trigger_config` varchar(1000)
        NULL
        COMMENT '事件触发配置(JSON文本)';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `applicable_area` varchar(1000)
        NULL
        COMMENT '适用区域/工序(JSON文本)';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `remark` varchar(500)
        NULL
        COMMENT '备注';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `create_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `update_time` datetime
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `tenant_id` bigint
        NOT NULL
        DEFAULT '0'
        COMMENT '租户编号';
