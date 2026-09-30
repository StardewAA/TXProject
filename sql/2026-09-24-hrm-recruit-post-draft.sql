-- 与 deploy/mysql/repair/2026-09-24-hrm-recruit-post-draft.sql 内容一致
-- HRM：招聘职位「草稿」状态字典（QA 报告 Q-003 / I-007）
-- 新建职位默认落草稿（status=3），草稿不出现在招聘门户；前端「发布」后置为 1。

INSERT INTO `system_dict_data`
  (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`)
SELECT 4, '草稿', '3', 'hrm_recruit_post_status', 0, 'default', '', '招聘职位草稿（未发布到门户）', 'hrm-dev', 'hrm-dev'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data`
                  WHERE `dict_type` = 'hrm_recruit_post_status' AND `value` = '3' AND `deleted` = b'0');

-- 校验：应返回 草稿/3 一行
-- SELECT label, value FROM system_dict_data WHERE dict_type = 'hrm_recruit_post_status' AND deleted = b'0';
