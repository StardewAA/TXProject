-- ============================================
-- GoView 大屏项目内容字段扩容
-- 表：report_go_view_project.content
-- 原类型：text（65535 字节）
-- 新类型：mediumtext（16777215 字节）
-- 原因：content 存的是整份大屏 JSON（画布配置 + 全部组件 + 请求配置），
--       中文按 UTF-8 占 3 字节，一个 40+ 组件的看板就要 80KB 上下，
--       原来的 text 会在保存时报 “Data too long for column 'content'”。
-- 说明：MODIFY COLUMN 本身幂等，重复执行无副作用。
-- ============================================

ALTER TABLE `report_go_view_project`
  MODIFY COLUMN `content` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '报表内容';
