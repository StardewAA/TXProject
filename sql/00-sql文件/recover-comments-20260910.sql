-- mes_set_* 列注释第三轮修复：截断型乱码（2026-09-10）
-- 前两轮各自的探测盲区（详见 codegen/mojibake.py）都源于同一个错误——
-- 拿"能不能还原"当"是不是乱码"的判据，于是坏得最狠的恰好被判成正常：
--   轮1 修好 61 列（完好可逆）；轮2 重建 53 列（含 '?'，字节已丢）；
--   本轮 33 列是**截断型**：尾部丢 1 个汉字，整串还原不了，前两轮都没检出。
-- 其中 10 列完全还原（证据），23 列补回丢失的那一个汉字（由上文唯一确定）。
-- 生成器：codegen/gen_recover_comments.py（幂等）

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `chemical_code` varchar(64)
        NULL
        COMMENT '危化品编码';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `chemical_name` varchar(200)
        NOT NULL
        COMMENT '危化品名称';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `result` varchar(8)
        NULL
        COMMENT '结果：PASS/FAIL';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `problem_desc` varchar(500)
        NULL
        COMMENT '异常/不合格描述';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `creator` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '创建者';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `updater` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '更新者';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `result` varchar(8)
        NULL
        COMMENT '结果：PASS/FAIL';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `problem_desc` varchar(500)
        NULL
        COMMENT '异常/不合格描述';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `creator` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '创建者';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `updater` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '更新者';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `wo_id` bigint
        NULL
        COMMENT '关联工单编号（事件触发型）';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `gas_type` varchar(16)
        NOT NULL
        COMMENT '气体类型：CO/H2S/O2/LEL/VOC/NH3/CL2';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `unit` varchar(16)
        NULL
        COMMENT '单位：mg/m3 / % / %LEL';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `result` varchar(8)
        NULL
        COMMENT '结果：PASS/FAIL';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `collection_mode` varchar(16)
        NOT NULL
        DEFAULT 'MANUAL'
        COMMENT '采集方式：IOT_AUTO/MANUAL';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `creator` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '创建者';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `updater` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '更新者';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `manifest_no` varchar(64)
        NOT NULL
        COMMENT '危废联单号';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `quantity_unit` varchar(16)
        NULL
        COMMENT '数量单位（吨等）';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `handler` varchar(64)
        NULL
        COMMENT '经办人';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `creator` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '创建者';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `updater` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '更新者';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `plan_type` varchar(16)
        NOT NULL
        DEFAULT 'PERIODIC'
        COMMENT '触发类型：PERIODIC(周期)/EVENT(事件)';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `creator` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '创建者';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `updater` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '更新者';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `review_by` varchar(64)
        NULL
        COMMENT '复核人';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `disposition` varchar(32)
        NULL
        COMMENT '处置方式：NORMAL_INBOUND/CONTROLLED_STORAGE/ISSUE_ALLOWED/REJECT_ISSUE/REUSE/DISCHARGE/ISOLATE_STORAGE/MARKED_STORAGE';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `creator` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '创建者';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `updater` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '更新者';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `ref_standard` varchar(100)
        NULL
        COMMENT '引用国标编号（GBZ/GB/T）';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `period_type` varchar(16)
        NULL
        COMMENT '周期类型：DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY/EVENT';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `creator` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '创建者';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `updater` varchar(64)
        NULL
        DEFAULT ''
        COMMENT '更新者';
