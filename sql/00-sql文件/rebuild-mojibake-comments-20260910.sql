-- mes_set_* 列注释重建（2026-09-10）
-- 与 fix-mojibake-comments-20260910.sql 的区别：
--   那份是**可逆还原**（encode('gbk') -> decode('utf-8')），61 列已修好；
--   本份是**重建**——另 53 列在当初写入时字符就无法映射、被替换成 '?'，原始字节已不存在，
--   没有算法能还原，只能按可读片段 + 同族列上下文逐条人工推断。
-- ⚠️ 重建是**有依据的推断而非原文**（例：'閫氶?)鍚堟牸锛?鏄?0鍚' 足以唯一确定 "通风)合格：1是/0否"），
--    执行前请过目 LABELS；若某条与你的记忆不符，改 codegen/gen_rebuild_comments.py 再重跑。
-- 生成器：codegen/gen_rebuild_comments.py（幂等，重复跑产出相同内容）

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `plan_id` bigint
        NULL
        COMMENT '关联检测计划编号';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `label_ok` tinyint(1)
        NOT NULL
        DEFAULT '1'
        COMMENT '标识完整性：1是/0否';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `msds_ok` tinyint(1)
        NOT NULL
        DEFAULT '1'
        COMMENT 'MSDS有效性：1是/0否';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `storage_ok` tinyint(1)
        NOT NULL
        DEFAULT '1'
        COMMENT '储存条件(温湿度/通风)合格：1是/0否';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `separation_ok` tinyint(1)
        NOT NULL
        DEFAULT '1'
        COMMENT '禁忌物分离合格：1是/0否';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `inspector` varchar(64)
        NULL
        COMMENT '检测人';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `inspect_time` datetime
        NOT NULL
        COMMENT '检测时间';

ALTER TABLE `mes_set_chemical_safety`
    MODIFY COLUMN `deleted` bit(1)
        NOT NULL
        DEFAULT b'0'
        COMMENT '是否删除';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `plan_id` bigint
        NULL
        COMMENT '关联检测计划编号';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `facility_name` varchar(200)
        NOT NULL
        COMMENT '设施名称(灭火器/消火栓/烟感/温感/应急照明/疏散指示等)';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `check_time` datetime
        NOT NULL
        COMMENT '检测时间';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `inspector` varchar(64)
        NULL
        COMMENT '检测人';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `photo_urls` varchar(2000)
        NULL
        COMMENT '检测照片URL(逗号分隔)';

ALTER TABLE `mes_set_fire_check`
    MODIFY COLUMN `deleted` bit(1)
        NOT NULL
        DEFAULT b'0'
        COMMENT '是否删除';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `plan_id` bigint
        NULL
        COMMENT '关联检测计划编号';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `location` varchar(200)
        NULL
        COMMENT '检测位置';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `concentration` decimal(10,3)
        NULL
        COMMENT '检测浓度值';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `limit_value` decimal(10,3)
        NULL
        COMMENT '限值';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `instrument_no` varchar(64)
        NULL
        COMMENT '检测仪器编号';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `inspector` varchar(64)
        NULL
        COMMENT '检测人';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `inspect_time` datetime
        NOT NULL
        COMMENT '检测时间';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `photo_urls` varchar(2000)
        NULL
        COMMENT '检测照片URL(逗号分隔)';

ALTER TABLE `mes_set_gas_record`
    MODIFY COLUMN `deleted` bit(1)
        NOT NULL
        DEFAULT b'0'
        COMMENT '是否删除';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `waste_code` varchar(64)
        NULL
        COMMENT '危废代码(如 HW08)';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `stage` varchar(16)
        NOT NULL
        DEFAULT 'GENERATED'
        COMMENT '台账环节：GENERATED(产生)/STORED(贮存)/TRANSFERRED(转移)/DISPOSED(处置)';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `storage_location` varchar(200)
        NULL
        COMMENT '贮存地点';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `counterparty` varchar(200)
        NULL
        COMMENT '交接方/接收单位';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `status` varchar(16)
        NOT NULL
        DEFAULT 'DRAFT'
        COMMENT '审批状态：DRAFT/APPROVED/REJECTED';

ALTER TABLE `mes_set_hazardous_waste`
    MODIFY COLUMN `deleted` bit(1)
        NOT NULL
        DEFAULT b'0'
        COMMENT '是否删除';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `period_type` varchar(16)
        NULL
        COMMENT '周期类型(周期型)：DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `start_date` date
        NULL
        COMMENT '生效开始日期';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `machinery_id` bigint
        NULL
        COMMENT '关联设备编号(事件/设备型)';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `operation_id` bigint
        NULL
        COMMENT '关联工序编号(事件/工单型)';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `standard_id` bigint
        NULL
        COMMENT '关联检测标准编号';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `assignee_id` bigint
        NULL
        COMMENT '责任人/执行人编号';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `status` varchar(16)
        NOT NULL
        DEFAULT 'DRAFT'
        COMMENT '状态：DRAFT/ACTIVE/STOPPED';

ALTER TABLE `mes_set_plan`
    MODIFY COLUMN `deleted` bit(1)
        NOT NULL
        DEFAULT b'0'
        COMMENT '是否删除';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `stage` varchar(32)
        NOT NULL
        COMMENT '环节：PURCHASE_INBOUND(采购入库)/MATERIAL_ISSUE(生产领用)/WASTE_INTERMEDIATE(中间废弃物)/FINISHED_PRODUCT(成品)';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `biz_no` varchar(64)
        NULL
        COMMENT '关联单号(入库单/领料单/报工单)';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `batch_no` varchar(64)
        NULL
        COMMENT '批次号';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `item_name` varchar(200)
        NOT NULL
        COMMENT '物料/产品名称(AI 据此初筛)';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `ai_result` varchar(16)
        NULL
        COMMENT 'AI 初筛结果：CLEAN(无污染)/POLLUTED(有污染)/UNCERTAIN(不确定)';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `ai_confidence` int
        NULL
        COMMENT 'AI 置信度(0-100)';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `review_result` varchar(16)
        NULL
        COMMENT '人工复核结果：CLEAN/POLLUTED；空=待复核';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `storage_method` varchar(200)
        NULL
        COMMENT '最终存储方法';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `marked` bit(1)
        NULL
        COMMENT '是否标记(有污染默认标记)';

ALTER TABLE `mes_set_pollution_check`
    MODIFY COLUMN `deleted` bit(1)
        NOT NULL
        DEFAULT b'0'
        COMMENT '是否删除';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `domain` varchar(16)
        NULL
        COMMENT '检测域：SAFETY/ENV/HEALTH';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `test_type` varchar(32)
        NULL
        COMMENT '检测类型：GAS/NOISE/DUST/RADIATION/ELECTRICAL/FIRE/CHEMICAL/PPE/PRESSURE等';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `limits_config` varchar(4000)
        NULL
        COMMENT '限值配置(JSON文本，如 {CO:{mac,pcTWA,unit}})';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `method` varchar(500)
        NULL
        COMMENT '检测方法描述';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `status` varchar(16)
        NOT NULL
        DEFAULT 'DRAFT'
        COMMENT '状态：DRAFT/ACTIVE/OBSOLETE';

ALTER TABLE `mes_set_standard`
    MODIFY COLUMN `deleted` bit(1)
        NOT NULL
        DEFAULT b'0'
        COMMENT '是否删除';
