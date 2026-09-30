-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- CA 签章台账：补 seal_file_size 列（契约《后端变更请求-投标文件CA签章递交衔接》A1）
-- 登记日期：2026-09-21
--
-- [为什么] 契约 A1：SealDocumentRespVO / CaSealRespVO 缺"签章后文件字节数"。
--   前端若把 document_file_id 取 sealFileId，则无法填 documentSize（递交入参 @NotNull）
--   → 硬阻塞"签章 → 递交"链路；取原文件虽可绕过，但证据链会指向未签章的原文件。
-- [业务目标] 让"签章产物"自带体积，前端一次调用即可完成签章 + 递交入参拼装。
-- [幂等] 用 information_schema 判定后再 ALTER，可重复执行。
-- ============================================================================

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bidding_ca_seal' AND COLUMN_NAME = 'seal_file_size'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `bidding_ca_seal` ADD COLUMN `seal_file_size` bigint DEFAULT NULL COMMENT ''已签章文件字节数（契约 A1；供前端填 documentSize）'' AFTER `seal_file_name`',
  'SELECT ''seal_file_size 已存在，跳过'' AS msg'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史行回填：已签章文件在 infra 侧有大小，但台账未存；此处不动（保持 NULL，由前端按需回查）。
-- 校验段
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bidding_ca_seal' AND COLUMN_NAME = 'seal_file_size';
