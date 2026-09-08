-- 招聘第一组增量脚本：职位状态字典、职位类型权限与候选人简历版本表
-- 仅新增对象，不删除或重建既有招聘数据。

-- 职位状态调整为：0 已暂停、1 已发布、2 已关闭。
-- 字典数据表没有 (dict_type, value) 唯一约束，使用“更新 + NOT EXISTS 插入”保证脚本可重复执行。
UPDATE `system_dict_data`
SET `sort` = 1, `label` = '已发布', `status` = 0, `color_type` = 'success', `css_class` = '',
    `remark` = '', `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `dict_type` = 'hrm_recruit_post_status' AND `value` = '1';

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '已发布', '1', 'hrm_recruit_post_status', 0, 'success', '', '',
       'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'hrm_recruit_post_status' AND `value` = '1'
);

UPDATE `system_dict_data`
SET `sort` = 2, `label` = '已暂停', `status` = 0, `color_type` = 'warning', `css_class` = '',
    `remark` = '', `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `dict_type` = 'hrm_recruit_post_status' AND `value` = '0';

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '已暂停', '0', 'hrm_recruit_post_status', 0, 'warning', '', '',
       'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'hrm_recruit_post_status' AND `value` = '0'
);

UPDATE `system_dict_data`
SET `sort` = 3, `label` = '已关闭', `status` = 0, `color_type` = 'danger', `css_class` = '',
    `remark` = '', `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `dict_type` = 'hrm_recruit_post_status' AND `value` = '2';

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '已关闭', '2', 'hrm_recruit_post_status', 0, 'danger', '', '',
       'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'hrm_recruit_post_status' AND `value` = '2'
);

UPDATE `system_dict_type`
SET `name` = 'HRM 招聘职位状态', `status` = 0, `remark` = '0 已暂停，1 已发布，2 已关闭',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0', `deleted_time` = NULL
WHERE `type` = 'hrm_recruit_post_status';

INSERT INTO `system_dict_type`
    (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`,
     `deleted`, `deleted_time`)
SELECT 'HRM 招聘职位状态', 'hrm_recruit_post_status', 0, '0 已暂停，1 已发布，2 已关闭',
       'admin', NOW(), 'admin', NOW(), b'0', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type` WHERE `type` = 'hrm_recruit_post_status'
);

CREATE TABLE IF NOT EXISTS `hrm_recruit_resume` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `candidate_id` bigint NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_url` varchar(1024) NOT NULL,
  `version_no` int NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 当前版本，1 已被替换',
  `remark` varchar(500) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_candidate_version` (`candidate_id`, `version_no`),
  KEY `idx_candidate_id` (`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HRM 候选人简历版本表';

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (1637, '职位类型', 'hrm:recruit:post-type:query', 2, 15, 1486, 'post-type', 'ep:files', 'hrm/recruit/post-type/index', 'HrmRecruitPostType', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `permission` = VALUES(`permission`), `component` = VALUES(`component`), `component_name` = VALUES(`component_name`), `deleted` = b'0';

INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(1638, '职位类型新增', 'hrm:recruit:post-type:create', 3, 1, 1637, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(1639, '职位类型修改', 'hrm:recruit:post-type:update', 3, 2, 1637, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(1640, '职位类型删除', 'hrm:recruit:post-type:delete', 3, 3, 1637, '', '', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');
