-- ============================================================
-- HRM 请假类型补充为「行业/通用假种模板」
--
-- 背景（测试报告 M-10）：原字典只有 年假/事假/病假/调休 4 种，
-- 无法覆盖施工企业常见的婚假、产假、陪产假、丧假、工伤假等，
-- 导致统计与薪资无法按假种区分费率。
--
-- 通用假种与扣款口径（薪资考勤规则侧）：
--   事假 → 全额扣（personalLeaveRate，默认 100%）
--   病假 → 按比例扣（sickLeaveRate，默认 60%）
--   年假 / 调休 / 婚假 / 产假 / 陪产假 / 丧假 / 工伤假 → 不扣款（法定或福利假）
-- 幂等：可重复执行
-- ============================================================

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`,
                                `css_class`, `remark`, `creator`, `updater`)
SELECT t.sort, t.label, t.value, 'hrm_attendance_leave_type', 0, t.color, '', t.remark,
       'hrm-leave-dev', 'hrm-leave-dev'
FROM (
    SELECT 5 AS sort, '婚假' AS label, '婚假' AS value, 'success' AS color, '法定 3 天（各地可延长）' AS remark
    UNION ALL SELECT 6, '产假', '产假', 'success', '法定产假'
    UNION ALL SELECT 7, '陪产假', '陪产假', 'success', '法定陪产假'
    UNION ALL SELECT 8, '丧假', '丧假', 'warning', '直系亲属丧假'
    UNION ALL SELECT 9, '工伤假', '工伤假', 'danger', '工伤停工留薪期'
    UNION ALL SELECT 10, '其他', '其他', 'default', '其他经批准的假种'
) t
WHERE NOT EXISTS (SELECT 1 FROM (SELECT label FROM `system_dict_data`
                                 WHERE dict_type = 'hrm_attendance_leave_type') d
                  WHERE d.label = t.label);

-- 核对：应返回 10 行
-- SELECT sort, label, value FROM system_dict_data WHERE dict_type = 'hrm_attendance_leave_type' ORDER BY sort;
