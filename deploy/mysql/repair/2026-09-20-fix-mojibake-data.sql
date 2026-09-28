-- 2026-09-20：修复数据库中残留的中文数据乱码（双重编码）。
-- 处理方式同 2026-09-20-fix-mojibake-comments.sql：CP1252 反向映射 + UTF-8 重新解码。
-- 可重复执行。
SET SESSION sql_mode='';
UPDATE `hrm_salary_group` SET `change_rule` = '按转正/调薪前后的工资混合计算' WHERE `id` = 1;
UPDATE `system_role` SET `name` = 'HRM团队负责人', `remark` = '本地团队工作台演示角色' WHERE `id` = 162;
