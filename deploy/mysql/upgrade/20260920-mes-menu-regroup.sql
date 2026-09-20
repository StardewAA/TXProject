-- =====================================================================
-- 20260920 MES 菜单目录调整（二级分组版）
--
-- 目标：把 MES 菜单从「平铺」调整为「二级分组」结构，共 11 个一级模块 / 70 个功能页。
--
-- 原则（严格）：
--   1) 不删除任何已有功能菜单；
--   2) 已有功能页的 id / path / component / permission 全部保持不变；
--   3) 仅新增 18 个二级分组目录（type=1）；
--   4) 仅调整已有功能页的 parent_id；
--   5) 按系统权限机制补齐角色-菜单授权（否则普通角色因父级缺失看不到子菜单）；
--   6) 同步租户套餐 menu_ids。
--
-- 幂等：可重复执行（INSERT IGNORE / NOT EXISTS 双重保护）。
-- 前置：执行前请确认 id 段 5102-5104 / 5301-5302 / 5501-5502 / 5701-5704 / 6001-6007 未被占用。
-- 后置：必须清 Redis 缓存（role:* / menu_role_ids:* / permission_menu_ids:* / user_role_ids:*）并重新登录。
--
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < 20260920-mes-menu-regroup.sql
-- 作者：刘洋    日期：2026-09-20
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 一、新增 18 个二级分组目录
--   基础数据(5101)：物料资料/往来单位/组织与编码
--   生产管理(5700)：生产计划/工艺管理/生产执行/异常管理
--   质量管理(5500)：质检配置/检验作业
--   设备管理(5300)：设备档案/维保管理
--   仓库管理(5780)：仓库基础/缺料预警/入库作业/出库作业/退货处理/调拨盘点/赋码追溯
-- ---------------------------------------------------------------------
INSERT IGNORE INTO system_menu
(id, name, permission, type, sort, parent_id, path, icon, component, component_name,
 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
(5102, '物料资料',   '', 1, 1, 5101, 'material',        'ep:box',               '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(5103, '往来单位',   '', 1, 2, 5101, 'partner',         'ep:connection',        '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(5104, '组织与编码', '', 1, 3, 5101, 'org',             'ep:office-building',   '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(5701, '生产计划',   '', 1, 1, 5700, 'plan',            'ep:calendar',          '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(5702, '工艺管理',   '', 1, 2, 5700, 'craft',           'ep:set-up',            '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(5703, '生产执行',   '', 1, 3, 5700, 'execute',         'ep:video-play',        '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(5704, '异常管理',   '', 1, 4, 5700, 'abnormal',        'ep:warning',           '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(5501, '质检配置',   '', 1, 1, 5500, 'config',          'ep:setting',           '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(5502, '检验作业',   '', 1, 2, 5500, 'inspect',         'ep:document-checked',  '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(5301, '设备档案',   '', 1, 1, 5300, 'archive',         'ep:notebook',          '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(5302, '维保管理',   '', 1, 2, 5300, 'maintain',        'ep:tools',             '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(6001, '仓库基础',   '', 1, 1, 5780, 'base',            'ep:house',             '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(6002, '缺料预警',   '', 1, 2, 5780, 'shortage',        'ep:bell',              '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(6003, '入库作业',   '', 1, 3, 5780, 'inbound',         'ep:download',          '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(6004, '出库作业',   '', 1, 4, 5780, 'outbound',        'ep:upload',            '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(6005, '退货处理',   '', 1, 5, 5780, 'return',          'ep:refresh-left',      '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(6006, '调拨盘点',   '', 1, 6, 5780, 'transfer-check',  'ep:sort',              '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
(6007, '赋码追溯',   '', 1, 7, 5780, 'trace',           'ep:connection',        '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ---------------------------------------------------------------------
-- 二、调整已有功能页 parent_id（只改 parent_id，其它字段一律不动）
-- ---------------------------------------------------------------------
-- ① 基础数据
UPDATE system_menu SET parent_id = 5102, update_time = NOW() WHERE id IN (5120, 5110, 5130);
UPDATE system_menu SET parent_id = 5103, update_time = NOW() WHERE id IN (5140, 5150);
UPDATE system_menu SET parent_id = 5104, update_time = NOW() WHERE id IN (5160, 5170, 5190);
-- ② 生产管理
UPDATE system_menu SET parent_id = 5701, update_time = NOW() WHERE id IN (5530, 5540);
UPDATE system_menu SET parent_id = 5702, update_time = NOW() WHERE id IN (5710, 5720);
UPDATE system_menu SET parent_id = 5703, update_time = NOW() WHERE id IN (5550, 5560, 5981);
UPDATE system_menu SET parent_id = 5704, update_time = NOW() WHERE id = 5750;
-- ③ 质量管理
UPDATE system_menu SET parent_id = 5501, update_time = NOW() WHERE id IN (5510, 5610, 5630);
UPDATE system_menu SET parent_id = 5502, update_time = NOW() WHERE id IN (5680, 5640, 5650, 5670, 5660, 5966);
-- ④ 设备管理（含：工具管理目录由一级降为二级）
UPDATE system_menu SET parent_id = 5301, update_time = NOW() WHERE id IN (5310, 5320);
UPDATE system_menu SET parent_id = 5302, update_time = NOW() WHERE id IN (5330, 5350, 5360, 5340, 5370);
UPDATE system_menu SET parent_id = 5300, update_time = NOW() WHERE id = 5400;
-- ⑤ 仓库管理
UPDATE system_menu SET parent_id = 6001, update_time = NOW() WHERE id IN (5781, 5786);
UPDATE system_menu SET parent_id = 6002, update_time = NOW() WHERE id = 5990;
UPDATE system_menu SET parent_id = 6003, update_time = NOW() WHERE id IN (5798, 5792, 5819, 5840, 5873, 5888);
UPDATE system_menu SET parent_id = 6004, update_time = NOW() WHERE id IN (5805, 5867, 5970, 5881, 5857);
UPDATE system_menu SET parent_id = 6005, update_time = NOW() WHERE id IN (5830, 5847);
UPDATE system_menu SET parent_id = 6006, update_time = NOW() WHERE id IN (5922, 5951, 5957);
UPDATE system_menu SET parent_id = 6007, update_time = NOW() WHERE id IN (5897, 5917, 5911);
-- ⑥ 项目管理升为一级
UPDATE system_menu SET parent_id = 5100, update_time = NOW() WHERE id = 6750;

-- ---------------------------------------------------------------------
-- 三、补齐角色-菜单授权（反向推导：谁有子菜单，就给谁补父级分组）
--    依据：MenuServiceImpl.filterDisableMenus 与 AuthConvert.buildMenuTree 均要求父级在角色菜单列表内，
--          否则子菜单悬空/被过滤。超管(super_admin)走全量菜单逻辑，无需处理。
-- ---------------------------------------------------------------------
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5102, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5120,5110,5130)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5102 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5103, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5140,5150)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5103 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5104, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5160,5170,5190)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5104 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5701, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5530,5540)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5701 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5702, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5710,5720)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5702 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5703, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5550,5560,5981)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5703 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5704, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5750)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5704 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5501, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5510,5610,5630)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5501 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5502, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5680,5640,5650,5670,5660,5966)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5502 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5301, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5310,5320)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5301 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5302, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5330,5350,5360,5340,5370)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5302 AND x.deleted=b'0');

-- 设备管理(5300) 回补：工具管理降级后，拥有工具管理的角色仍需可见设备管理
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 5300, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5400)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=5300 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 6001, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5781,5786)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=6001 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 6002, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5990)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=6002 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 6003, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5798,5792,5819,5840,5873,5888)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=6003 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 6004, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5805,5867,5970,5881,5857)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=6004 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 6005, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5830,5847)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=6005 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 6006, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5922,5951,5957)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=6006 AND x.deleted=b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, 6007, '1', NOW(), '1', NOW(), b'0', rm.tenant_id FROM system_role_menu rm
 WHERE rm.deleted = b'0' AND rm.menu_id IN (5897,5917,5911)
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=6007 AND x.deleted=b'0');

-- ---------------------------------------------------------------------
-- 四、同步租户套餐 menu_ids（仅对已包含锚点父目录的套餐追加）
-- ---------------------------------------------------------------------
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5102), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5101 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5102 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5103), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5101 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5103 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5104), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5101 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5104 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5701), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5700 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5701 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5702), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5700 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5702 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5703), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5700 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5703 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5704), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5700 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5704 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5501), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5500 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5501 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5502), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5500 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5502 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5301), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5300 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5301 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',5302), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5300 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5302 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',6001), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5780 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(6001 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',6002), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5780 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(6002 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',6003), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5780 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(6003 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',6004), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5780 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(6004 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',6005), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5780 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(6005 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',6006), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5780 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(6006 AS JSON));
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(CAST(menu_ids AS JSON),'$',6007), update_time=NOW()
 WHERE deleted=b'0' AND JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(5780 AS JSON)) AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON),CAST(6007 AS JSON));

-- ---------------------------------------------------------------------
-- 五、校验（执行后请人工核对下述结果）
-- ---------------------------------------------------------------------
-- 5.1 新增分组是否齐全（期望 18 行）
SELECT id, name, parent_id, type, sort, path FROM system_menu
 WHERE id IN (5102,5103,5104,5701,5702,5703,5704,5501,5502,5301,5302,6001,6002,6003,6004,6005,6006,6007) ORDER BY id;
-- 5.2 是否存在孤儿菜单（期望 0 行）
SELECT c.id, c.name, c.parent_id FROM system_menu c
 WHERE c.deleted=0 AND c.type IN (1,2) AND c.parent_id<>0
   AND NOT EXISTS (SELECT 1 FROM system_menu p WHERE p.id=c.parent_id AND p.deleted=0);
-- 5.3 普通角色是否存在「有子菜单但缺父级」（期望 0 行）
SELECT rm.role_id, rm.tenant_id, m.id AS menu_id, m.name, m.parent_id
  FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0
 WHERE rm.deleted=0 AND rm.role_id<>1 AND m.type IN (1,2) AND m.parent_id<>0
   AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                    WHERE x.role_id=rm.role_id AND x.menu_id=m.parent_id AND x.deleted=0);

-- ---------------------------------------------------------------------
-- 六、执行后必须清理 Redis 缓存并重新登录：
--   role:*  |  menu_role_ids:*  |  permission_menu_ids:*  |  user_role_ids:*
-- ---------------------------------------------------------------------
