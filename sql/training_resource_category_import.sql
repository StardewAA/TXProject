-- ==================================================================================
-- 云培训学院 - 模块一「资源中心 → 分类与标签」课程分类数据导入脚本
--
-- 背景：
--   资源中心「分类与标签」不再单独展示「课程分类」档位，而是将课程管理
--   「分类管理」维护的课程分类数据（training_course_category，域-组-课 三级）
--   分别导入到 doc / video / question 三类分类下，每类保留一份独立数据。
--   导入后，后端通过 code 字段保持三类分类同步，做到「改一处、三处同步」。
--
-- 执行影响：
--   1) 清理现有 doc/video/question 分类下的原始联调/种子数据；
--   2) 将 training_course_category 中非删除记录按层级导入到 training_category
--      的 doc / video / question 三个 type 下，并复用课程分类 code；
--   3) 被清理分类引用的文档/视频资源仅解除分类关联（category_id 置空）。
--
-- 安全：按条件匹配，重复执行会先清理再导入；不触碰 training_course_category 数据。
--
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_resource_category_import.sql
-- ==================================================================================

-- 0. 确保 training_category 存在 code 字段（用于跨类型同步）
SELECT COUNT(*) INTO @code_exists FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'training_category' AND column_name = 'code';
SET @add_code_sql = IF(@code_exists = 0,
    'ALTER TABLE `training_category` ADD COLUMN `code` VARCHAR(64) NULL COMMENT \'分类编码，用于 doc/video/question 三类之间同步\'',
    'SELECT 1');
PREPARE stmt FROM @add_code_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 0.1 为跨类型同步查询加速
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'training_category' AND index_name = 'idx_tcat_type_code';
SET @add_idx_sql = IF(@idx_exists = 0,
    'CREATE INDEX `idx_tcat_type_code` ON `training_category` (`type`, `code`)',
    'SELECT 1');
PREPARE idx_stmt FROM @add_idx_sql;
EXECUTE idx_stmt;
DEALLOCATE PREPARE idx_stmt;

-- 1. 解除文档资源对被清理分类的引用（保留资源本体，仅置空分类）
UPDATE `training_document_resource`
SET `category_id` = NULL
WHERE `category_id` IN (
    SELECT `id` FROM (SELECT `id` FROM `training_category` WHERE `type` IN ('doc','video','question') AND `deleted` = b'0') t
);

-- 2. 解除视频资源对被清理分类的引用（保留资源本体，仅置空分类）
UPDATE `training_video_resource`
SET `category_id` = NULL
WHERE `category_id` IN (
    SELECT `id` FROM (SELECT `id` FROM `training_category` WHERE `type` IN ('doc','video','question') AND `deleted` = b'0') t
);

-- 3. 逻辑删除现有 doc/video/question 分类数据（避免与导入数据重复/冲突）
UPDATE `training_category`
SET `deleted` = b'1'
WHERE `type` IN ('doc','video','question') AND `deleted` = b'0';

-- 4. 导入课程分类到 doc / video / question（每类一份，共三份）
-- 4.1 一级：域
INSERT INTO `training_category` (`type`, `name`, `code`, `parent_id`, `sort`, `tenant_id`)
SELECT 'doc', cc.name, cc.code, 0, cc.sort, cc.tenant_id
FROM `training_course_category` cc
WHERE cc.level = 1 AND cc.deleted = b'0';

INSERT INTO `training_category` (`type`, `name`, `code`, `parent_id`, `sort`, `tenant_id`)
SELECT 'video', cc.name, cc.code, 0, cc.sort, cc.tenant_id
FROM `training_course_category` cc
WHERE cc.level = 1 AND cc.deleted = b'0';

INSERT INTO `training_category` (`type`, `name`, `code`, `parent_id`, `sort`, `tenant_id`)
SELECT 'question', cc.name, cc.code, 0, cc.sort, cc.tenant_id
FROM `training_course_category` cc
WHERE cc.level = 1 AND cc.deleted = b'0';

-- 4.2 二级：组
INSERT INTO `training_category` (`type`, `name`, `code`, `parent_id`, `sort`, `tenant_id`)
SELECT 'doc', cc.name, cc.code, p.id, cc.sort, cc.tenant_id
FROM `training_course_category` cc
JOIN `training_course_category` pcc ON pcc.id = cc.parent_id
JOIN `training_category` p ON p.type = 'doc' AND p.code = pcc.code AND p.deleted = b'0'
WHERE cc.level = 2 AND cc.deleted = b'0';

INSERT INTO `training_category` (`type`, `name`, `code`, `parent_id`, `sort`, `tenant_id`)
SELECT 'video', cc.name, cc.code, p.id, cc.sort, cc.tenant_id
FROM `training_course_category` cc
JOIN `training_course_category` pcc ON pcc.id = cc.parent_id
JOIN `training_category` p ON p.type = 'video' AND p.code = pcc.code AND p.deleted = b'0'
WHERE cc.level = 2 AND cc.deleted = b'0';

INSERT INTO `training_category` (`type`, `name`, `code`, `parent_id`, `sort`, `tenant_id`)
SELECT 'question', cc.name, cc.code, p.id, cc.sort, cc.tenant_id
FROM `training_course_category` cc
JOIN `training_course_category` pcc ON pcc.id = cc.parent_id
JOIN `training_category` p ON p.type = 'question' AND p.code = pcc.code AND p.deleted = b'0'
WHERE cc.level = 2 AND cc.deleted = b'0';

-- 4.3 三级：课
INSERT INTO `training_category` (`type`, `name`, `code`, `parent_id`, `sort`, `tenant_id`)
SELECT 'doc', cc.name, cc.code, p.id, cc.sort, cc.tenant_id
FROM `training_course_category` cc
JOIN `training_course_category` pcc ON pcc.id = cc.parent_id
JOIN `training_category` p ON p.type = 'doc' AND p.code = pcc.code AND p.deleted = b'0'
WHERE cc.level = 3 AND cc.deleted = b'0';

INSERT INTO `training_category` (`type`, `name`, `code`, `parent_id`, `sort`, `tenant_id`)
SELECT 'video', cc.name, cc.code, p.id, cc.sort, cc.tenant_id
FROM `training_course_category` cc
JOIN `training_course_category` pcc ON pcc.id = cc.parent_id
JOIN `training_category` p ON p.type = 'video' AND p.code = pcc.code AND p.deleted = b'0'
WHERE cc.level = 3 AND cc.deleted = b'0';

INSERT INTO `training_category` (`type`, `name`, `code`, `parent_id`, `sort`, `tenant_id`)
SELECT 'question', cc.name, cc.code, p.id, cc.sort, cc.tenant_id
FROM `training_course_category` cc
JOIN `training_course_category` pcc ON pcc.id = cc.parent_id
JOIN `training_category` p ON p.type = 'question' AND p.code = pcc.code AND p.deleted = b'0'
WHERE cc.level = 3 AND cc.deleted = b'0';
