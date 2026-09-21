-- =============================================================================
-- 客户级别（客户等级 crm_customer_level）字典对齐
--
-- 【背景】
--   系统初始化脚本（sql/00-sql文件/sql/*/ruoyi-vue-pro.sql）里该字典只有 3 项
--   （A/B/C），缺少「潜在客户」，因此其他人拉取代码、重建数据库后，
--   客户 / 线索表单里的「客户级别」下拉框与当前环境不一致（少一项）。
--
-- 【目标】
--   把该字典统一为 4 项，与当前环境完全一致：
--       1 = A （重点客户）    color_type = primary
--       2 = B （普通客户）    color_type = info
--       3 = C （非优先客户）  color_type = default
--       4 = 潜在客户
--
-- 【特性】
--   * 幂等：可重复执行，结果一致；
--   * 存在则对齐文案 / 排序 / 颜色，不存在则补插（含恢复被逻辑删除的记录）；
--   * 非 1~4 的多余项：在「没有被客户 / 线索引用」的前提下做逻辑删除，
--     保证下拉框内容完全一致，同时不破坏存量数据展示。
--
-- 【执行】
--   mysql --default-character-set=utf8mb4 -uroot -p ruoyi-vue-pro < 本文件
--   执行后请清除字典缓存（或等缓存过期）：
--     redis-cli --scan --pattern '*dict*' | xargs -r redis-cli del
-- =============================================================================

-- 1. 字典类型：缺失则补建（存在则对齐名称）
UPDATE system_dict_type
SET name = '客户等级', status = 0, remark = 'CRM 客户等级',
    updater = '1', update_time = NOW()
WHERE type = 'crm_customer_level' AND deleted = b'0';

INSERT INTO system_dict_type (name, type, status, remark, creator, create_time, updater, update_time, deleted)
SELECT '客户等级', 'crm_customer_level', 0, 'CRM 客户等级', '1', NOW(), '1', NOW(), b'0'
FROM (SELECT 1) AS dummy
WHERE NOT EXISTS (
    SELECT 1 FROM system_dict_type x
    WHERE x.type = 'crm_customer_level' AND x.deleted = b'0'
);

-- 2. 四项字典数据：已存在（含被逻辑删除的）则对齐并恢复
UPDATE system_dict_data
SET label = CASE value
                WHEN '1' THEN 'A （重点客户）'
                WHEN '2' THEN 'B （普通客户）'
                WHEN '3' THEN 'C （非优先客户）'
                WHEN '4' THEN '潜在客户'
            END,
    sort = CAST(value AS UNSIGNED),
    status = 0,
    color_type = CASE value
                     WHEN '1' THEN 'primary'
                     WHEN '2' THEN 'info'
                     WHEN '3' THEN 'default'
                     ELSE ''
                 END,
    remark = CASE value
                 WHEN '4' THEN '尚未成交'
                 ELSE CONCAT('CRM 客户等级 ', value)
             END,
    updater = '1',
    update_time = NOW(),
    deleted = b'0'
WHERE dict_type = 'crm_customer_level'
  AND value IN ('1', '2', '3', '4');

-- 3. 缺失的项补插（保证四项齐全）
INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT s.sort, s.label, s.value, 'crm_customer_level', 0, s.color_type, '', s.remark, '1', NOW(), '1', NOW(), b'0'
FROM (
              SELECT 1 AS sort, 'A （重点客户）'   AS label, '1' AS value, 'primary' AS color_type, 'CRM 客户等级 1' AS remark
    UNION ALL SELECT 2,        'B （普通客户）',            '2',        'info',                      'CRM 客户等级 2'
    UNION ALL SELECT 3,        'C （非优先客户）',          '3',        'default',                   'CRM 客户等级 3'
    UNION ALL SELECT 4,        '潜在客户',                  '4',        '',                          '尚未成交'
) s
WHERE NOT EXISTS (
    SELECT 1 FROM system_dict_data x
    WHERE x.dict_type = 'crm_customer_level' AND x.value = s.value AND x.deleted = b'0'
);

-- 4. 清理多余项：非 1~4 且没有被客户 / 线索引用的项做逻辑删除
UPDATE system_dict_data d
SET d.deleted = b'1', d.updater = '1', d.update_time = NOW()
WHERE d.dict_type = 'crm_customer_level'
  AND d.deleted = b'0'
  AND d.value NOT IN ('1', '2', '3', '4')
  AND NOT EXISTS (
      SELECT 1 FROM crm_customer c
      WHERE c.deleted = 0 AND c.level = CAST(d.value AS UNSIGNED)
  )
  AND NOT EXISTS (
      SELECT 1 FROM crm_clue cl
      WHERE cl.deleted = 0 AND cl.level = CAST(d.value AS UNSIGNED)
  );

-- 5. 自查：应返回 4 行（1/2/3/4，deleted=0）
SELECT id, sort, label, value, dict_type, status, color_type, deleted
FROM system_dict_data
WHERE dict_type = 'crm_customer_level' AND deleted = b'0'
ORDER BY sort;
