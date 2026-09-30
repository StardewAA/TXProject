-- =============================================================
-- 薪资组新增「适用岗位」维度
-- 背景：一线计件（防腐工/保温工/喷砂工…）与二线（职能/管理，基本+绩效）薪资规则不同；
--      原来薪资组只能按「部门 / 指定员工」圈定范围，新增岗位时要逐个加人。
--      增加「适用岗位名称」后，新建岗位只需建一套薪资组并绑岗位，即可自动适配该岗位的人。
-- 说明：员工档案里的岗位是 `hrm_employee.post_name`（文本），因此这里按岗位名称匹配。
-- 幂等：可重复执行
-- =============================================================

SET @db := DATABASE();

SET @sql := (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `hrm_salary_group` ADD COLUMN `post_names` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''适用岗位名称列表(JSON)''',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'hrm_salary_group' AND COLUMN_NAME = 'post_names');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
