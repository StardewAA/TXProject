-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 病毒扫描字段统一（2026-09-20）：cert_file_versions.virus_scan_status → virus_status
-- 依据：《招投标模块开发规范》§5 三-5（上传须过病毒扫描与类型白名单；先例：资质域 virus_scan_status）
--   + 外部公告附件参考实现 bidding_notice_external_attachment.virus_status（清单 §1.1 标为"✅ 参考实现"）
--   + 《文件接口收敛方案-后端实施篇-2026-09-20》：统一字段名 virusStatus，值域 UNKNOWN/PENDING/CLEAN/INFECTED，
--     INFECTED 一律拒绝下发（此前 CERT_FILE_VIRUS_NOT_CLEAN 定义了却从未抛出）。
-- 说明：仅改名，值域兼容（原 PENDING/CLEAN/INFECTED 全部保留为合法值，新增 UNKNOWN）。
-- ============================================================================

ALTER TABLE `cert_file_versions`
  CHANGE COLUMN `virus_scan_status` `virus_status` varchar(16) NOT NULL DEFAULT 'PENDING'
  COMMENT '病毒扫描状态 code：UNKNOWN/PENDING/CLEAN/INFECTED（统一口径；INFECTED 拒绝预览/下载）';

-- ============================================================================
-- 校验 SELECT（执行后人工核对）
-- SHOW COLUMNS FROM cert_file_versions LIKE 'virus%';
-- ============================================================================
