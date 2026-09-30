-- HRM QA 修复脚本（2026-09-23 第二轮测试报告）
-- 幂等：可重复执行
--
-- 覆盖：
--   ISS-01  员工导入模板下载 500 —— 缺失字典 hrm_employee_id_type，补齐字典与字典项
--   ISS-02  租户超管（tenant_admin）HRM 权限倒挂 —— 补齐缺失的 HRM 权限点授权
--   ISS-03  「候选人新增」权限点被逻辑删除 —— 恢复权限点并授权给已有候选人管理权限的角色
--
-- 执行后必须清理权限缓存（否则仍返回 403）：
--   redis-cli -n 0 --scan --pattern 'menu_role_ids:*'      | xargs -r redis-cli -n 0 DEL
--   redis-cli -n 0 --scan --pattern 'permission_menu_ids:*' | xargs -r redis-cli -n 0 DEL
--   redis-cli -n 0 --scan --pattern 'user_role_ids:*'       | xargs -r redis-cli -n 0 DEL

-- ---------------------------------------------------------------- ISS-01
INSERT INTO system_dict_type (name, type, status, remark, creator, create_time, updater, update_time, deleted)
SELECT 'HRM 员工证件类型', 'hrm_employee_id_type', 0, '员工导入模板「证件类型」下拉字典', 'hrm-qa-fix', NOW(), 'hrm-qa-fix', NOW(), 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE type = 'hrm_employee_id_type' AND deleted = 0);

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted)
SELECT * FROM (
    SELECT 1 AS sort, '身份证'     AS label, '1' AS value, 'hrm_employee_id_type' AS dict_type, 0 AS status, 'default' AS color_type, '' AS css_class, '' AS remark, 'hrm-qa-fix' AS creator, NOW() AS create_time, 'hrm-qa-fix' AS updater, NOW() AS update_time, 0 AS deleted
    UNION ALL SELECT 2, '港澳通行证', '2', 'hrm_employee_id_type', 0, 'default', '', '', 'hrm-qa-fix', NOW(), 'hrm-qa-fix', NOW(), 0
    UNION ALL SELECT 3, '台湾通行证', '3', 'hrm_employee_id_type', 0, 'default', '', '', 'hrm-qa-fix', NOW(), 'hrm-qa-fix', NOW(), 0
    UNION ALL SELECT 4, '护照',       '4', 'hrm_employee_id_type', 0, 'default', '', '', 'hrm-qa-fix', NOW(), 'hrm-qa-fix', NOW(), 0
    UNION ALL SELECT 5, '其他',       '5', 'hrm_employee_id_type', 0, 'default', '', '', 'hrm-qa-fix', NOW(), 'hrm-qa-fix', NOW(), 0
) t
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'hrm_employee_id_type' AND deleted = 0);

-- ---------------------------------------------------------------- ISS-03
-- 恢复被逻辑删除的「候选人新增」权限点（此前 deleted=1，导致全角色都拿不到该权限）
UPDATE system_menu
SET deleted = 0, status = 0, updater = 'hrm-qa-fix', update_time = NOW()
WHERE id = 1493 AND permission = 'hrm:recruit:candidate:create';

-- 把该权限点授权给「已具备候选人查询/修改权限」的角色（等价于原先支持候选人管理的角色集合）
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
SELECT DISTINCT rm.role_id, 1493, 'hrm-qa-fix', NOW(), 'hrm-qa-fix', NOW(), 0
FROM system_role_menu rm
JOIN system_menu m ON m.id = rm.menu_id
WHERE m.permission IN ('hrm:recruit:candidate:update', 'hrm:recruit:candidate:query')
  AND rm.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                  WHERE x.role_id = rm.role_id AND x.menu_id = 1493 AND x.deleted = 0);

-- ---------------------------------------------------------------- ISS-02
-- 租户超管补齐 HRM 权限点（此前 tenant_admin 缺 33 个 HRM 权限，出现“超管 403、HR 可操作”的倒挂）
-- 按角色 code = 'tenant_admin' 匹配，避免写死某个环境的 role_id
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted)
SELECT r.id, m.id, 'hrm-qa-fix', NOW(), 'hrm-qa-fix', NOW(), 0
FROM system_menu m
JOIN system_role r ON r.code = 'tenant_admin' AND r.deleted = 0
WHERE m.permission LIKE 'hrm:%'
  AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm
                  WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = 0);
