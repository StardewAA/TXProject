-- =============================================================
-- 修复：文件/图片地址硬编码本机域名（http://127.0.0.1:48080）
--
-- 现象：换机器部署、或用局域网地址（如 http://192.168.113.121:5173）访问时，
--      浏览器会去连「访问者自己电脑」的 48080 端口，导致
--      缩略图裂图、预览打不开、下载失败。合同模板、员工附件、证照、打卡照片、
--      简历等都存在同一问题。
--
-- 处理：
--   1) 文件存储配置（数据库存储，master）的 domain 置空
--      → 以后新上传的文件一律返回相对路径 /admin-api/infra/file/{configId}/get/xxx，
--        任何环境、任何访问地址都能正常显示与下载；
--   2) 回填历史数据：去掉存量地址里的本机域名前缀（相对路径由前端按当前访问域名解析）。
--
-- 幂等：可重复执行。
-- 回滚：若确实需要「外部系统能直接打开的绝对地址」，把 domain 配成该环境对外域名即可，例如
--      UPDATE infra_file_config SET config = REPLACE(config, '"domain":""', '"domain":"http://192.168.113.121:48080"')
--      WHERE storage = 1;
-- =============================================================

-- ---------- 1) 文件存储配置：domain 置空（新上传即相对路径） ----------
UPDATE `infra_file_config`
SET `config` = REPLACE(REPLACE(`config`,
                              '"domain":"http://127.0.0.1:48080"', '"domain":""'),
                       '"domain":"http://localhost:48080"', '"domain":""'),
    `update_time` = NOW()
WHERE `storage` = 1
  AND (`config` LIKE '%127.0.0.1%' OR `config` LIKE '%localhost%');

-- ---------- 2) 回填历史数据 ----------

-- 2.1 文件服务元数据：url 既是展示地址，也是 getFileContentByUrl 的匹配键，
--     必须与置空 domain 后的新格式保持一致
UPDATE `infra_file`
SET `url` = REPLACE(REPLACE(`url`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `url` LIKE '%127.0.0.1%' OR `url` LIKE '%localhost%';

-- 2.2 合同模板：缩略图 / 预览 / 下载
UPDATE `hrm_contract_template`
SET `file_url` = REPLACE(REPLACE(`file_url`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `file_url` LIKE '%127.0.0.1%' OR `file_url` LIKE '%localhost%';

UPDATE `hrm_contract_template`
SET `thumbnail_url` = REPLACE(REPLACE(`thumbnail_url`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `thumbnail_url` LIKE '%127.0.0.1%' OR `thumbnail_url` LIKE '%localhost%';

UPDATE `hrm_contract_template`
SET `preview_url` = REPLACE(REPLACE(`preview_url`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `preview_url` LIKE '%127.0.0.1%' OR `preview_url` LIKE '%localhost%';

-- 2.3 员工材料附件
UPDATE `hrm_employee_file`
SET `url` = REPLACE(REPLACE(`url`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `url` LIKE '%127.0.0.1%' OR `url` LIKE '%localhost%';

-- 2.4 员工劳动合同附件
UPDATE `hrm_employee_contract`
SET `file_urls` = REPLACE(REPLACE(`file_urls`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `file_urls` LIKE '%127.0.0.1%' OR `file_urls` LIKE '%localhost%';

-- 2.5 合同签署记录附件（扫描件/签署文件）
UPDATE `hrm_contract_sign_record`
SET `file_urls` = REPLACE(REPLACE(`file_urls`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `file_urls` LIKE '%127.0.0.1%' OR `file_urls` LIKE '%localhost%';

-- 2.6 员工证照（附件）
UPDATE `hrm_employee_certificate`
SET `attachment_url` = REPLACE(REPLACE(`attachment_url`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `file_urls` = REPLACE(REPLACE(`file_urls`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `attachment_url` LIKE '%127.0.0.1%' OR `attachment_url` LIKE '%localhost%'
   OR `file_urls` LIKE '%127.0.0.1%' OR `file_urls` LIKE '%localhost%';

-- 2.7 现场打卡照片（人脸/定位打卡留痕）
UPDATE `hrm_attendance_clock`
SET `photo_url` = REPLACE(REPLACE(`photo_url`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `photo_url` LIKE '%127.0.0.1%' OR `photo_url` LIKE '%localhost%';

-- 2.8 招聘简历附件
UPDATE `hrm_recruit_resume`
SET `file_url` = REPLACE(REPLACE(`file_url`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `file_url` LIKE '%127.0.0.1%' OR `file_url` LIKE '%localhost%';

-- 2.9 个人求职简历附件
UPDATE `hrm_personal_resume`
SET `file_url` = REPLACE(REPLACE(`file_url`, 'http://127.0.0.1:48080', ''), 'http://localhost:48080', ''),
    `update_time` = NOW()
WHERE `file_url` LIKE '%127.0.0.1%' OR `file_url` LIKE '%localhost%';

-- ---------- 3) 校验：以下统计应全部为 0 ----------
SELECT 'infra_file.url' AS col, COUNT(*) AS cnt FROM `infra_file`
    WHERE `url` LIKE '%127.0.0.1%' OR `url` LIKE '%localhost%'
UNION ALL SELECT 'hrm_contract_template.file_url', COUNT(*) FROM `hrm_contract_template`
    WHERE `file_url` LIKE '%127.0.0.1%' OR `file_url` LIKE '%localhost%'
UNION ALL SELECT 'hrm_employee_file.url', COUNT(*) FROM `hrm_employee_file`
    WHERE `url` LIKE '%127.0.0.1%' OR `url` LIKE '%localhost%'
UNION ALL SELECT 'hrm_employee_contract.file_urls', COUNT(*) FROM `hrm_employee_contract`
    WHERE `file_urls` LIKE '%127.0.0.1%' OR `file_urls` LIKE '%localhost%'
UNION ALL SELECT 'hrm_contract_sign_record.file_urls', COUNT(*) FROM `hrm_contract_sign_record`
    WHERE `file_urls` LIKE '%127.0.0.1%' OR `file_urls` LIKE '%localhost%'
UNION ALL SELECT 'hrm_employee_certificate', COUNT(*) FROM `hrm_employee_certificate`
    WHERE `attachment_url` LIKE '%127.0.0.1%' OR `file_urls` LIKE '%127.0.0.1%'
       OR `attachment_url` LIKE '%localhost%' OR `file_urls` LIKE '%localhost%'
UNION ALL SELECT 'hrm_attendance_clock.photo_url', COUNT(*) FROM `hrm_attendance_clock`
    WHERE `photo_url` LIKE '%127.0.0.1%' OR `photo_url` LIKE '%localhost%'
UNION ALL SELECT 'hrm_recruit_resume.file_url', COUNT(*) FROM `hrm_recruit_resume`
    WHERE `file_url` LIKE '%127.0.0.1%' OR `file_url` LIKE '%localhost%'
UNION ALL SELECT 'hrm_personal_resume.file_url', COUNT(*) FROM `hrm_personal_resume`
    WHERE `file_url` LIKE '%127.0.0.1%' OR `file_url` LIKE '%localhost%'
UNION ALL SELECT 'infra_file_config(domain)', COUNT(*) FROM `infra_file_config`
    WHERE `storage` = 1 AND (`config` LIKE '%127.0.0.1%' OR `config` LIKE '%localhost%');
