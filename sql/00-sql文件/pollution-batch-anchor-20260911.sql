-- =====================================================================
-- MES 安全环保检测 - 污染判定记录锚定批次（在库/入库物品环保检测）
-- 依据：《MES在库环保体检-需求分析与设计-2026-09-11.md》§5.2
--
-- 问题：判定记录只有 batch_no 字符串，且由人工在表单里打字录入。
--       实测 69 条在用记录中，仅 37 条(54%)能 join 回 mes_wm_batch，
--       单号仅 10 条(14%)能 join 回真实单据 —— 判定与库存是两份互不约束的数据。
--
-- 改法：加 batch_id 外键，batch_no 降级为显示快照（沿用本表 location/location_id
--       已有的「名称快照」约定，不发明新模式）。
--       入口由「选择题」改为「从在库/入库记录选」后，batch_no 不再是人打的，
--       因此 refreshBatchStamp / isBatchFrozen 等既有投影逻辑一行都不用改。
--
-- 附带收益：join 由字符串等值变整数主键等值，顺带绕开本库的排序规则分裂
--           （mes_set_* 是 utf8mb4_0900_ai_ci，mes_wm_* 是 utf8mb4_unicode_ci），
--           新代码不再需要 COLLATE。
--
-- 存量：32 条（租户1 12 条 / 租户2010 20 条）无法关联，按决策「保留 + 标未关联」
--       保持 NULL，不做臆测回填；页面按 batch_id IS NULL 标出并可筛。
--
-- 索引：本表原只有 PRIMARY(id) 与 uk_record_no(record_no)，按批次反查是全表扫，
--       idx_batch_id 非可选。
--
-- 幂等：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 守卫。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < 本文件
-- =====================================================================

SET NAMES utf8mb4;

-- 1) 加列 batch_id
SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'mes_set_pollution_check'
                      AND COLUMN_NAME = 'batch_id');
SET @ddl := IF(@exist_col = 0,
  'ALTER TABLE `mes_set_pollution_check`
     ADD COLUMN `batch_id` bigint DEFAULT NULL COMMENT ''批次编号（mes_wm_batch.id）；空=历史手工录入未关联，非"无批次"'' AFTER `biz_no`',
  'SELECT ''skip: batch_id 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 加索引 idx_batch_id
SET @exist_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'mes_set_pollution_check'
                      AND INDEX_NAME = 'idx_batch_id');
SET @ddl := IF(@exist_idx = 0,
  'ALTER TABLE `mes_set_pollution_check` ADD INDEX `idx_batch_id` (`batch_id`)',
  'SELECT ''skip: idx_batch_id 已存在'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 同步 batch_no 的列注释：它现在是快照，不是录入值
ALTER TABLE `mes_set_pollution_check`
  MODIFY COLUMN `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号（显示快照，权威值见 batch_id；空=历史未关联记录）';

-- 4) 核对
SELECT COUNT(*) AS 判定总数,
       SUM(batch_id IS NOT NULL) AS 已关联,
       SUM(batch_id IS NULL) AS 未关联
  FROM `mes_set_pollution_check` WHERE deleted = 0;
