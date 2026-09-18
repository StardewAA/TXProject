-- MES 工具模块 - 单据菜单 + 字典初始化

-- ========== 1. 字典类型（4 个单据状态，已存在则跳过）==========
INSERT INTO system_dict_type (name, type, status, remark, creator, updater)
SELECT 'MES 工具领用单状态', 'mes_tm_tool_issue_status', 0, '工具领用单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT type FROM system_dict_type WHERE type = 'mes_tm_tool_issue_status') x);

INSERT INTO system_dict_type (name, type, status, remark, creator, updater)
SELECT 'MES 工具归还单状态', 'mes_tm_tool_return_status', 0, '工具归还单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT type FROM system_dict_type WHERE type = 'mes_tm_tool_return_status') x);

INSERT INTO system_dict_type (name, type, status, remark, creator, updater)
SELECT 'MES 工具维修单状态', 'mes_tm_tool_repair_status', 0, '工具维修单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT type FROM system_dict_type WHERE type = 'mes_tm_tool_repair_status') x);

INSERT INTO system_dict_type (name, type, status, remark, creator, updater)
SELECT 'MES 工具报废单状态', 'mes_tm_tool_scrap_status', 0, '工具报废单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT type FROM system_dict_type WHERE type = 'mes_tm_tool_scrap_status') x);

-- ========== 2. 字典数据（已存在同 type+value 则跳过）==========
INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater)
SELECT 1, '草稿', '0', 'mes_tm_tool_issue_status', 0, 'info', '', '工具领用单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_dict_data WHERE dict_type = 'mes_tm_tool_issue_status' AND value = '0') x);

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater)
SELECT 2, '已确认', '1', 'mes_tm_tool_issue_status', 0, 'success', '', '工具领用单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_dict_data WHERE dict_type = 'mes_tm_tool_issue_status' AND value = '1') x);

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater)
SELECT 1, '草稿', '0', 'mes_tm_tool_return_status', 0, 'info', '', '工具归还单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_dict_data WHERE dict_type = 'mes_tm_tool_return_status' AND value = '0') x);

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater)
SELECT 2, '已确认', '1', 'mes_tm_tool_return_status', 0, 'success', '', '工具归还单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_dict_data WHERE dict_type = 'mes_tm_tool_return_status' AND value = '1') x);

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater)
SELECT 1, '草稿', '0', 'mes_tm_tool_repair_status', 0, 'info', '', '工具维修单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_dict_data WHERE dict_type = 'mes_tm_tool_repair_status' AND value = '0') x);

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater)
SELECT 2, '已确认', '1', 'mes_tm_tool_repair_status', 0, 'success', '', '工具维修单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_dict_data WHERE dict_type = 'mes_tm_tool_repair_status' AND value = '1') x);

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater)
SELECT 3, '维修完成', '2', 'mes_tm_tool_repair_status', 0, 'primary', '', '工具维修单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_dict_data WHERE dict_type = 'mes_tm_tool_repair_status' AND value = '2') x);

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater)
SELECT 1, '草稿', '0', 'mes_tm_tool_scrap_status', 0, 'info', '', '工具报废单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_dict_data WHERE dict_type = 'mes_tm_tool_scrap_status' AND value = '0') x);

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater)
SELECT 2, '已确认', '1', 'mes_tm_tool_scrap_status', 0, 'success', '', '工具报废单状态', '1', '1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM system_dict_data WHERE dict_type = 'mes_tm_tool_scrap_status' AND value = '1') x);

-- ========== 3. 菜单（父级：5400 工具管理）==========
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, creator, updater) VALUES
(5430, '工具领用', '', 2, 3, 5400, 'tool/issue',  'ep:share',    'mes/tm/toolissue/index',  'MesTmToolIssue',  0, '1', '1'),
(5440, '工具归还', '', 2, 4, 5400, 'tool/return', 'ep:refresh-left', 'mes/tm/toolreturn/index', 'MesTmToolReturn', 0, '1', '1'),
(5450, '工具维修', '', 2, 5, 5400, 'tool/repair', 'ep:tools',    'mes/tm/toolrepair/index', 'MesTmToolRepair', 0, '1', '1'),
(5460, '工具报废', '', 2, 6, 5400, 'tool/scrap',  'ep:delete',   'mes/tm/toolscrap/index',  'MesTmToolScrap',  0, '1', '1');

-- 工具领用 按钮
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, status, creator, updater) VALUES
(5431, '查询', 'mes:tm-tool-issue:query',  3, 1, 5430, 0, '1', '1'),
(5432, '新增', 'mes:tm-tool-issue:create', 3, 2, 5430, 0, '1', '1'),
(5433, '修改', 'mes:tm-tool-issue:update', 3, 3, 5430, 0, '1', '1'),
(5434, '删除', 'mes:tm-tool-issue:delete', 3, 4, 5430, 0, '1', '1'),
(5435, '导出', 'mes:tm-tool-issue:export', 3, 5, 5430, 0, '1', '1');

-- 工具归还 按钮
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, status, creator, updater) VALUES
(5441, '查询', 'mes:tm-tool-return:query',  3, 1, 5440, 0, '1', '1'),
(5442, '新增', 'mes:tm-tool-return:create', 3, 2, 5440, 0, '1', '1'),
(5443, '修改', 'mes:tm-tool-return:update', 3, 3, 5440, 0, '1', '1'),
(5444, '删除', 'mes:tm-tool-return:delete', 3, 4, 5440, 0, '1', '1'),
(5445, '导出', 'mes:tm-tool-return:export', 3, 5, 5440, 0, '1', '1');

-- 工具维修 按钮
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, status, creator, updater) VALUES
(5451, '查询', 'mes:tm-tool-repair:query',  3, 1, 5450, 0, '1', '1'),
(5452, '新增', 'mes:tm-tool-repair:create', 3, 2, 5450, 0, '1', '1'),
(5453, '修改', 'mes:tm-tool-repair:update', 3, 3, 5450, 0, '1', '1'),
(5454, '删除', 'mes:tm-tool-repair:delete', 3, 4, 5450, 0, '1', '1'),
(5455, '导出', 'mes:tm-tool-repair:export', 3, 5, 5450, 0, '1', '1');

-- 工具报废 按钮
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, status, creator, updater) VALUES
(5461, '查询', 'mes:tm-tool-scrap:query',  3, 1, 5460, 0, '1', '1'),
(5462, '新增', 'mes:tm-tool-scrap:create', 3, 2, 5460, 0, '1', '1'),
(5463, '修改', 'mes:tm-tool-scrap:update', 3, 3, 5460, 0, '1', '1'),
(5464, '删除', 'mes:tm-tool-scrap:delete', 3, 4, 5460, 0, '1', '1'),
(5465, '导出', 'mes:tm-tool-scrap:export', 3, 5, 5460, 0, '1', '1');
