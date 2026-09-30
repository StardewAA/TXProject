-- 010 OSS 提升补传机制（2026-09-25）
--
-- 公开资源经服务端绑定确认（bindFile 且 accessType=PUBLIC、业务类型允许公开）之后，
-- 内容会被复制到公开 OSS（config_id=36），url 改写为 OSS 直连。若提升因网络抖动
-- （如 OSS TLS 握手中断）失败，文件会停在 master（config_id=4），但仍经网关匿名可读。
--
-- 本迁移增加「可追踪、可安全重试」的字段，使这类 stranded 文件能被运维/定时任务
-- 识别并补传，而不是永久停留在 master：
--   promote_status      0 无/未提升  1 处理中  2 已提升  3 失败
--   promote_fail_count  累计失败次数（避免无限重试，运维可观测）
--   promote_last_error  最后一次失败信息（截断 512，便于排障）
--
-- 约束：仅对「已确认公开」的文件做补传；私有文件（access_type=2）绝不触碰，
-- 绝不迁移到公共桶。master 配置保持 4 不变。

ALTER TABLE infra_file
  ADD COLUMN promote_status      TINYINT      NOT NULL DEFAULT 0 COMMENT 'OSS提升状态: 0无/1处理中/2已提升/3失败',
  ADD COLUMN promote_fail_count  INT          NOT NULL DEFAULT 0 COMMENT 'OSS提升累计失败次数',
  ADD COLUMN promote_last_error  VARCHAR(512) DEFAULT NULL     COMMENT 'OSS提升最后一次失败信息';

-- 历史已提升（公开且内容已在 OSS）的记录回填为 2，避免被定时补传任务误判为 stranded。
UPDATE infra_file
   SET promote_status = 2
 WHERE access_type = 1
   AND bind_status = 2
   AND config_id = 36;
