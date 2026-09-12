-- ======================================================================
-- 资源中心增强（2026-09-11）
--   1) 文档资源库：新增「总页数 page_count」
--      - 新增文档时自动识别文档页数
--      - 预估阅读时长 est_minutes 自动按 「总页数 × 10 分钟/页」设置
--   2) 考试题库：新增「所属分类 category_id」
--      - 复用 training_category 表（type=question），支持一级/二级/三级分类
--
-- 幂等：重复执行不会报错（MySQL 不支持 ADD COLUMN IF NOT EXISTS，用存储过程判断）
-- 涉及表：training_document_resource / training_question
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 文档资源表：新增 page_count
-- ----------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_add_doc_page_count`;
DELIMITER $$
CREATE PROCEDURE `sp_add_doc_page_count`()
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'training_document_resource'
                     AND COLUMN_NAME = 'page_count') THEN
        ALTER TABLE `training_document_resource`
            ADD COLUMN `page_count` int DEFAULT NULL COMMENT '文档总页数（上传时自动识别）' AFTER `file_size`;
    END IF;
END$$
DELIMITER ;
CALL `sp_add_doc_page_count`();
DROP PROCEDURE IF EXISTS `sp_add_doc_page_count`;

-- ----------------------------------------------------------------------
-- 2. 试题表：新增 category_id（所属分类，复用训练分类表 question 类型）
-- ----------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_add_question_category_id`;
DELIMITER $$
CREATE PROCEDURE `sp_add_question_category_id`()
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'training_question'
                     AND COLUMN_NAME = 'category_id') THEN
        ALTER TABLE `training_question`
            ADD COLUMN `category_id` bigint DEFAULT NULL COMMENT '所属分类ID（资源分类question类型，支持三级）' AFTER `difficulty`;
    END IF;
    IF NOT EXISTS (SELECT 1
                   FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'training_question'
                     AND INDEX_NAME = 'idx_tq_category') THEN
        ALTER TABLE `training_question`
            ADD KEY `idx_tq_category` (`category_id`);
    END IF;
END$$
DELIMITER ;
CALL `sp_add_question_category_id`();
DROP PROCEDURE IF EXISTS `sp_add_question_category_id`;
