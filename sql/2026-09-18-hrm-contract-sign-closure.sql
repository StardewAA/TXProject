-- ============================================================
-- HRM 合同签署闭环修复
--   1) 修复 hrm_contract_sign_record.file_urls 列类型
--   2) 存量数据回填：已签记录对应的合同置为「执行中」并回写签署时间
-- 执行库：ruoyi-vue-pro
-- 背景：9-17 建表时 file_urls 建成了 json 列，但 DO 使用 StringListTypeHandler
--       （按 varchar 存逗号分隔的地址串），导致一旦上传签署件就报
--       "Invalid JSON text ... for column 'hrm_contract_sign_record.file_urls'"。
--       改为 varchar(2048)，与 hrm_employee_contract.file_urls 保持一致。
-- ============================================================

-- 【1】列类型修复（幂等：重复执行等价）
ALTER TABLE `hrm_contract_sign_record`
  MODIFY COLUMN `file_urls` varchar(2048) DEFAULT NULL COMMENT '签署件附件地址列表（与 hrm_employee_contract.file_urls 一致，存逗号分隔的地址串）';

-- 【2】存量回填：已有「已签」签署记录的合同，状态置为 1 执行中并回写签署时间
UPDATE `hrm_employee_contract` c
JOIN (
  SELECT `contract_id`, MAX(`sign_time`) AS `sign_time`
  FROM `hrm_contract_sign_record`
  WHERE `deleted` = b'0' AND `status` = 1
  GROUP BY `contract_id`
) s ON s.`contract_id` = c.`id`
SET c.`status` = 1,
    c.`sign_time` = IFNULL(c.`sign_time`, IFNULL(s.`sign_time`, NOW())),
    c.`updater` = 'admin',
    c.`update_time` = NOW()
WHERE c.`deleted` = b'0' AND c.`status` <> 1;

-- 【3】核对
SELECT `id`, `contract_id`, `employee_id`, `status`, `sign_way`, `sign_time`, `file_urls`
FROM `hrm_contract_sign_record`
WHERE `deleted` = b'0'
ORDER BY `id` DESC;
