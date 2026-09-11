-- ======================================================================
-- 云培训学院 - 资源中心增强：视频清晰度 / 多码率播放
-- 覆盖需求：
--   ② 视频资源库：支持短视频与录播 MP4 上传，记录原始清晰度，
--      并在网络不稳定时允许选择更低清晰度观看（清晰度列表 + 多码率地址）
-- 说明：文档资源库（PDF/Word/WPS）与考试题库（AI 导入）无需新增表字段，
--       分别通过扩展上传白名单与新增 AI 导入接口实现。
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_resource_enhance.sql
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 视频资源表：视频类型（短视频/录播）、原始清晰度、可播放清晰度列表
-- ----------------------------------------------------------------------
ALTER TABLE `training_video_resource`
  ADD COLUMN `video_type` tinyint DEFAULT 2 COMMENT '视频类型：1 短视频 2 录播' AFTER `category_id`,
  ADD COLUMN `definition` varchar(16) DEFAULT NULL COMMENT '原始清晰度：2160P/1080P/720P/480P/360P' AFTER `duration_seconds`,
  ADD COLUMN `definition_options` varchar(2000) DEFAULT NULL COMMENT '可播放清晰度列表(JSON)：[{definition,url,transcoded}]，用于弱网切换低清晰度' AFTER `definition`;

-- ----------------------------------------------------------------------
-- 2. 历史数据兜底：已存在视频默认按"录播 + 原始清晰度 720P"处理，
--    清晰度列表置空（由服务端按原始地址自动生成降级项）
-- ----------------------------------------------------------------------
UPDATE `training_video_resource`
SET `video_type` = 2
WHERE `video_type` IS NULL;
