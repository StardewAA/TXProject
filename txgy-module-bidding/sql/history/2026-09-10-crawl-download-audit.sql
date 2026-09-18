-- 编码声明：强制 utf8mb4（防乱码）
SET NAMES utf8mb4;
-- ============================================================================
-- 2026-09-10 原文下载审计（bidding_crawl_download_audit）
-- 规范依据：用户口径 2026-09-10「必须增加严格的监控指标：下载速度监测（实时记录下载速率、
--   确保带宽使用合理、防范异常流量）、文件完整性与状态监测（MD5/SHA 校验、文件大小、格式合规性、存储状态）」；
--   《招投标模块开发规范》红线 8（file_id + sha256 + size）。
-- 业务目标：把"每次原文下载"变成可审计台账——量（byte_size）、速（speed_bps）、摘要（sha256）、
--   完整性结论（integrity_verdict）、归档状态（storage_status/file_id），支撑带宽异常发现与文件完整性追溯。
-- 只追加、不修改（重试会改写 task 行，审计必须不可变）。
-- 幂等：CREATE TABLE IF NOT EXISTS（可重复执行，无 DROP/TRUNCATE）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS bidding_crawl_download_audit (
  id                 bigint       NOT NULL COMMENT '主键（雪花）',
  task_id            bigint       DEFAULT NULL COMMENT '采集任务 id（列表直采/同步路径可能为空）',
  source_code        varchar(32)  DEFAULT NULL COMMENT '采集源 code',
  url                varchar(1024) NOT NULL COMMENT '下载地址',
  download_kind      varchar(24)  NOT NULL COMMENT '下载类型：DETAIL_HTML/PDF/ARCHIVE_HTML/ATTACHMENT/CHANNEL',
  http_status        int          DEFAULT NULL COMMENT 'HTTP 状态码（本地失败为空）',
  byte_size          bigint       DEFAULT NULL COMMENT '下载字节数',
  duration_ms        bigint       DEFAULT NULL COMMENT '耗时（毫秒）',
  speed_bps          bigint       DEFAULT NULL COMMENT '下载速率（字节/秒）',
  sha256             char(64)     DEFAULT NULL COMMENT '内容 SHA-256（红线 8 三件套之一）',
  integrity_verdict  varchar(24)  DEFAULT NULL COMMENT '完整性结论：PASS/FAIL_EMPTY/FAIL_TOO_LARGE/FAIL_FORMAT/FAIL_TEXT_TOO_SHORT/FAIL_ERROR_PAGE/FAIL_MOJIBAKE',
  storage_status     varchar(16)  DEFAULT NULL COMMENT '存储状态：NONE/REGISTERED/FAILED',
  file_id            bigint       DEFAULT NULL COMMENT '归档后的平台文件 id',
  message            varchar(512) DEFAULT NULL COMMENT '备注/失败原因',
  creator            varchar(64)  DEFAULT '' COMMENT '创建者',
  create_time        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater            varchar(64)  DEFAULT '' COMMENT '更新者',
  update_time        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted            bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  KEY idx_dl_audit_task (task_id),
  KEY idx_dl_audit_time (create_time),
  KEY idx_dl_audit_verdict (integrity_verdict),
  KEY idx_dl_audit_source (source_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原文下载审计（下载量/速率/摘要/完整性/存储状态）';

SELECT COUNT(*) AS audit_rows FROM bidding_crawl_download_audit;
