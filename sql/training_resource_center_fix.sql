-- ======================================================================
-- 云培训学院 - 资源中心表结构修正：补齐 MyBatis-Plus 基础字段
-- ----------------------------------------------------------------------
-- 问题现象：
--   考试题库（资源中心）Excel 批量导入提示「成功 0 条，失败 N 条」，
--   失败原因是每行绑定标签时报：
--     Unknown column 'update_time' in 'field list'
--     INSERT INTO training_question_tag (... create_time, update_time, creator, updater ...)
-- 根本原因：
--   training_question_tag / training_resource_reference 建表时遗漏了
--   update_time、updater 两列，而对应 DO（QuestionTagDO、ResourceReferenceDO）
--   均继承 TenantBaseDO，MyBatis-Plus 插入时会自动带上这两个基础字段。
-- 影响面：
--   - training_question_tag：试题一次性绑定标签（Excel 导入、AI 导入、保存试题）
--   - training_resource_reference：引用登记（试卷/课程引用文档、视频、试题）
-- 说明：
--   新建库直接执行 training_resource_center.sql 即可（已同步修正）；
--   已建库的历史环境执行本脚本补齐字段。
-- 执行：
--   mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_resource_center_fix.sql
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 试题-标签关联表：补 更新者 / 更新时间
-- ----------------------------------------------------------------------
ALTER TABLE `training_question_tag`
  ADD COLUMN `updater`     varchar(64) DEFAULT '' COMMENT '更新者' AFTER `creator`,
  ADD COLUMN `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`;

-- ----------------------------------------------------------------------
-- 2. 资源引用表：补 更新者 / 更新时间
-- ----------------------------------------------------------------------
ALTER TABLE `training_resource_reference`
  ADD COLUMN `updater`     varchar(64) DEFAULT '' COMMENT '更新者' AFTER `creator`,
  ADD COLUMN `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`;
