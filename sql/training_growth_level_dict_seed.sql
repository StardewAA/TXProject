-- 云培训学院 - 成长等级权威字典（六级人才成长体系）
-- 数据来源：《云培训学院-成长等级表.xlsx》、《数据字典与一级子模块关联》
-- 关联模块：模块二 课程成长等级标记(course_level)、模块三 学徒进阶培训班定级、
--           模块七 VR 项目成长标签、模块九 人才成长进度/技师-学徒等级分布
-- 说明：
--   1) 新建 training_growth_level 基础字典表（等级编码/序号/定位/独立作业权限/带徒上限/人工评审/参考对标）
--   2) 清理课程模块旧「四级成长体系」占位（MIDDLE 骨干 不在六级体系内），字段注释升级为六级
--   3) 导入 6 条权威等级数据（学徒→高级技师）
--   4) 注册后台菜单 41025「成长等级维护」（课程管理目录下）
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_growth_level_dict_seed.sql

-- 1. 建表（可重复执行）
CREATE TABLE IF NOT EXISTS `training_growth_level` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '等级ID',
  `code` varchar(20) NOT NULL COMMENT '等级编码(唯一)：APPRENTICE学徒 JUNIOR初级工 INTERMEDIATE中级工 SENIOR高级工 TECHNICIAN技师 SENIOR_TECHNICIAN高级技师',
  `level_no` int NOT NULL COMMENT '等级序号：1学徒 2初级工 3中级工 4高级工 5技师 6高级技师',
  `name` varchar(50) NOT NULL COMMENT '等级名称',
  `position_desc` varchar(255) DEFAULT NULL COMMENT '定位描述',
  `work_permission` varchar(255) DEFAULT NULL COMMENT '独立作业权限',
  `max_apprentice` int NOT NULL DEFAULT 0 COMMENT '带徒上限(人)',
  `need_review` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否需人工评审：0否 1是',
  `reference` varchar(100) DEFAULT NULL COMMENT '参考对标',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  UNIQUE KEY `uk_level_no` (`level_no`),
  KEY `idx_name` (`name`),
  KEY `idx_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成长等级表（六级人才成长体系字典）';

-- 2. 清理课程模块旧「四级成长体系」占位并升级字段注释（幂等）
--    旧值 MIDDLE(骨干) 不在权威六级体系内，置空；APPRENTICE/JUNIOR/SENIOR 语义已按六级对齐无需变更
UPDATE `training_course` SET `course_level` = NULL WHERE `course_level` = 'MIDDLE';
ALTER TABLE `training_course`
  MODIFY COLUMN `course_level` varchar(20) DEFAULT NULL COMMENT '成长等级标记：APPRENTICE学徒 JUNIOR初级工 INTERMEDIATE中级工 SENIOR高级工 TECHNICIAN技师 SENIOR_TECHNICIAN高级技师（对应 training_growth_level 六级字典）';

-- 3. 导入 6 条权威成长等级（学徒→高级技师；按 code/level_no 幂等更新）
INSERT INTO `training_growth_level`
  (`code`, `level_no`, `name`, `position_desc`, `work_permission`, `max_apprentice`, `need_review`, `reference`, `sort`, `remark`, `creator`, `tenant_id`) VALUES
('APPRENTICE', 1, '学徒', '入门，跟随作业', '否（全程监护）', 0, b'0', '初级工前', 1, '最低等级：处于入门跟随阶段，须在师傅监护下作业，不可带徒', 'admin', 1),
('JUNIOR', 2, '初级工', '能在监督下完成单项工序', '限简单工序', 0, b'0', '五级/初级', 2, '可承担简单工序作业但需监督，不可带徒', 'admin', 1),
('INTERMEDIATE', 3, '中级工', '独立完成主体工序', '是（本工种常规）', 0, b'0', '四级/中级', 3, '可独立完成本工种常规作业，尚不具备带徒资格', 'admin', 1),
('SENIOR', 4, '高级工', '处理复杂工况与缺陷返修', '是（含复杂工况）', 2, b'0', '三级/高级', 4, '可带 2 名学徒，负责复杂工况与缺陷返修', 'admin', 1),
('TECHNICIAN', 5, '技师', '工艺优化、班组技术负责', '是+技术决策', 5, b'0', '二级/技师', 5, '班组技术负责人，可带 5 名学徒，参与工艺优化', 'admin', 1),
('SENIOR_TECHNICIAN', 6, '高级技师', '跨工种攻关、标准与课程编写', '是+方案审核', 99, b'1', '一级/高级技师', 6, '最高等级：跨工种技术攻关、标准与课程编写，晋级需人工评审，带徒不设上限', 'admin', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `position_desc` = VALUES(`position_desc`),
  `work_permission` = VALUES(`work_permission`),
  `max_apprentice` = VALUES(`max_apprentice`),
  `need_review` = VALUES(`need_review`),
  `reference` = VALUES(`reference`),
  `sort` = VALUES(`sort`),
  `remark` = VALUES(`remark`),
  `updater` = 'admin';

-- 4. 注册后台菜单「成长等级维护」（41037 课程管理 41003 目录下，可重复执行；41025-41036 已被资源中心按钮占用）
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
SELECT 41037, '成长等级维护', 'training:course:query', 2, 60, 41003, 'level', 'lucide:medal', 'training/course/level/index', 'TrainingCourseGrowthLevel', 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 41037 AND `deleted` = b'0');
