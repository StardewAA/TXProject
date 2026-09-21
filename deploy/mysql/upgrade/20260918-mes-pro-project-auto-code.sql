-- ============================================
-- MES 项目管理 - 项目编码自动生成规则
-- 规则：PRO_PROJECT_CODE
-- 示例：PRJ202609170001
-- 组成：PRJ（固定字符） + yyyyMMdd（当前日期） + 4位流水号
-- ============================================

-- 1) 规则头（幂等）
INSERT INTO mes_md_auto_code_rule (code, name, description, max_length, padded, padded_char, padded_method, status, creator, updater, deleted, tenant_id)
SELECT 'PRO_PROJECT_CODE', '项目编码', '项目编码自动生成规则', 15, b'0', NULL, NULL, 0, '1', '1', b'0', 1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM mes_md_auto_code_rule r WHERE r.code = 'PRO_PROJECT_CODE' AND r.deleted = 0 AND r.tenant_id = 1
);

-- 2) 段一：固定字符前缀 PRJ（幂等）
INSERT INTO mes_md_auto_code_part (rule_id, sort, type, length, date_format, fix_character, serial_start_no, serial_step, cycle_flag, creator, updater, deleted, tenant_id)
SELECT r.id, 1, 3, 3, NULL, 'PRJ', NULL, NULL, b'0', '1', '1', b'0', 1
FROM mes_md_auto_code_rule r
WHERE r.code = 'PRO_PROJECT_CODE' AND r.deleted = 0 AND r.tenant_id = 1
AND NOT EXISTS (
  SELECT 1 FROM mes_md_auto_code_part p WHERE p.rule_id = r.id AND p.sort = 1 AND p.deleted = 0
);

-- 3) 段二：当前日期 yyyyMMdd（幂等）
INSERT INTO mes_md_auto_code_part (rule_id, sort, type, length, date_format, fix_character, serial_start_no, serial_step, cycle_flag, creator, updater, deleted, tenant_id)
SELECT r.id, 2, 2, 8, 'yyyyMMdd', NULL, NULL, NULL, b'0', '1', '1', b'0', 1
FROM mes_md_auto_code_rule r
WHERE r.code = 'PRO_PROJECT_CODE' AND r.deleted = 0 AND r.tenant_id = 1
AND NOT EXISTS (
  SELECT 1 FROM mes_md_auto_code_part p WHERE p.rule_id = r.id AND p.sort = 2 AND p.deleted = 0
);

-- 4) 段三：4位流水号（幂等）
INSERT INTO mes_md_auto_code_part (rule_id, sort, type, length, date_format, fix_character, serial_start_no, serial_step, cycle_flag, creator, updater, deleted, tenant_id)
SELECT r.id, 3, 4, 4, NULL, NULL, 1, 1, b'0', '1', '1', b'0', 1
FROM mes_md_auto_code_rule r
WHERE r.code = 'PRO_PROJECT_CODE' AND r.deleted = 0 AND r.tenant_id = 1
AND NOT EXISTS (
  SELECT 1 FROM mes_md_auto_code_part p WHERE p.rule_id = r.id AND p.sort = 3 AND p.deleted = 0
);
