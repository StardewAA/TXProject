-- 云培训学院：视频资源库异步上传 + 多清晰度转码（360P / 480P / 720P）
-- 分支：feat/training-video-async-upload-transcode
-- 依赖：training_video_resource（已有）

-- ----------------------------------------------------------------------
-- 1. 视频转码任务表
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `training_video_transcode_task` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `video_id`     bigint       NOT NULL COMMENT '视频资源ID',
  `definition`   varchar(16)  NOT NULL COMMENT '目标清晰度：2160P/1080P/720P/480P/360P',
  `height`       int          DEFAULT NULL COMMENT '目标画面高度（像素）',
  `status`       tinyint      NOT NULL DEFAULT 0 COMMENT '任务状态：0待转码 1转码中 2成功 3失败 4跳过',
  `progress`     int          NOT NULL DEFAULT 0 COMMENT '进度百分比（0-100）',
  `source_path`  varchar(500) DEFAULT NULL COMMENT '源文件相对路径',
  `output_path`  varchar(500) DEFAULT NULL COMMENT '转码输出相对路径',
  `file_size`    bigint       DEFAULT NULL COMMENT '输出文件大小（字节）',
  `command`      varchar(1000) DEFAULT NULL COMMENT 'ffmpeg 执行命令（排障用）',
  `error_msg`    varchar(500) DEFAULT NULL COMMENT '失败原因',
  `retry_count`  int          NOT NULL DEFAULT 0 COMMENT '重试次数',
  `start_time`   datetime     DEFAULT NULL COMMENT '开始时间',
  `end_time`     datetime     DEFAULT NULL COMMENT '结束时间',
  `creator`      varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`      varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id`    bigint       NOT NULL DEFAULT 1 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tvtt_video` (`video_id`),
  KEY `idx_tvtt_video_definition` (`video_id`, `definition`),
  KEY `idx_tvtt_status` (`status`),
  KEY `idx_tvtt_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频转码任务表';

-- ----------------------------------------------------------------------
-- 2. 视频表补充转码字段（已存在则跳过，兼容历史库）
-- ----------------------------------------------------------------------
-- definition_options / transcode_status 已在 training_resource_center.sql 中创建，
-- 此处仅在缺失时补齐（MySQL 不支持 ADD COLUMN IF NOT EXISTS，用存储过程兜底）。
DROP PROCEDURE IF EXISTS `txgy_add_video_transcode_cols`;
DELIMITER $$
CREATE PROCEDURE `txgy_add_video_transcode_cols`()
BEGIN
  IF NOT EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_video_resource'
        AND COLUMN_NAME = 'definition_options') THEN
    ALTER TABLE `training_video_resource`
      ADD COLUMN `definition_options` varchar(1000) DEFAULT NULL
      COMMENT '可播放清晰度列表（JSON）：[{definition,url,transcoded,original}]' AFTER `definition`;
  END IF;
  IF NOT EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_video_resource'
        AND COLUMN_NAME = 'transcode_status') THEN
    ALTER TABLE `training_video_resource`
      ADD COLUMN `transcode_status` tinyint NOT NULL DEFAULT 0
      COMMENT '转码状态：0未转码 1排队 2处理中 3成功 4失败' AFTER `definition_options`;
  END IF;
END$$
DELIMITER ;
CALL `txgy_add_video_transcode_cols`();
DROP PROCEDURE IF EXISTS `txgy_add_video_transcode_cols`;
