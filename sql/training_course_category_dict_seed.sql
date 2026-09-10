-- ======================================================================
-- 云培训学院 - 课程分类权威字典（8 域 - 33 组 - 112 课）
-- 数据来源：《云培训学院-课程分类表.xlsx》（赵升茹）、《数据字典与一级子模块关联》
-- 说明：
--   1) training_course_category 升级为三级树(域/组/课)，补充 std_hours 标准学时、
--      is_required 必修、biz_category 业务类别等字典字段，供模块二课程管理、模块一
--      资源打标、模块三/四/五/九绑课与学时统计关联；
--   2) 清理早期联调/种子测试数据（旧 13+13 seed 分类、资源中心 it 占位、测试课程）；
--   3) 幂等：可重复执行（已导入 code 先逻辑删除再重建）。
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_course_category_dict_seed.sql
-- ======================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------
-- 1. 结构升级：补充树形与字典字段（逐列幂等，MySQL 无 ADD COLUMN IF NOT EXISTS）
-- ----------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_course_category' AND COLUMN_NAME = 'parent_id');
SET @s := IF(@c = 0, 'ALTER TABLE `training_course_category` ADD COLUMN `parent_id` bigint NOT NULL DEFAULT 0 COMMENT ''父级分类ID(0=一级域)'' AFTER `id`', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_course_category' AND COLUMN_NAME = 'level');
SET @s := IF(@c = 0, 'ALTER TABLE `training_course_category` ADD COLUMN `level` tinyint NOT NULL DEFAULT 1 COMMENT ''层级:1域 2组 3课'' AFTER `parent_id`', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_course_category' AND COLUMN_NAME = 'biz_category');
SET @s := IF(@c = 0, 'ALTER TABLE `training_course_category` ADD COLUMN `biz_category` varchar(200) DEFAULT NULL COMMENT ''业务类别/适用工程队(域级)'' AFTER `level`', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_course_category' AND COLUMN_NAME = 'std_hours');
SET @s := IF(@c = 0, 'ALTER TABLE `training_course_category` ADD COLUMN `std_hours` int DEFAULT NULL COMMENT ''标准学时(课程级)'' AFTER `biz_category`', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_course_category' AND COLUMN_NAME = 'is_required');
SET @s := IF(@c = 0, 'ALTER TABLE `training_course_category` ADD COLUMN `is_required` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否必修(课程级)'' AFTER `std_hours`', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'training_course_category' AND COLUMN_NAME = 'status');
SET @s := IF(@c = 0, 'ALTER TABLE `training_course_category` ADD COLUMN `status` varchar(20) NOT NULL DEFAULT ''active'' COMMENT ''状态:active启用 inactive停用'' AFTER `sort`', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
-- ----------------------------------------------------------------------
-- 2. 清理初始测试数据（幂等）
--    2.1 旧版课程分类：早期 13 类 seed + 模块二 13 类行业分类 seed + 资源中心 it 占位
-- ----------------------------------------------------------------------
UPDATE `training_course_category`
SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0'
  AND (`id` <= 26 OR `code` LIKE 'it-cat-%' OR `creator` IN ('seed', 'it'));

--    2.2 早期接口联调自动造数课程及其媒资/评论/审核/学习引用（TC-DEMO/Auto Course/DetailDevVerify/M2-IT/RefProtectDemo 等）
SET @cids := '1,2095108962740559874,2095109136305053697,2095109140079927297,2095109193985122305,2095109196635922433,2095123490098339841,2095123494305226754,2095123555835666433,2095355801240866817,2095361595190566913,2095362229566464002,2095927582770274305,2095927586331238402,2095927730095202306,2095927733232541698,2096798079955525634,2096798094325211137,2096798162910470145,2096881401507373057';

UPDATE `training_course` SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND FIND_IN_SET(`id`, @cids);
UPDATE `training_course_media` SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND FIND_IN_SET(`course_id`, @cids);
UPDATE `training_course_comment` SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND FIND_IN_SET(`course_id`, @cids);
UPDATE `training_course_audit_log` SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND FIND_IN_SET(`course_id`, @cids);
UPDATE `training_course_learning_ref` SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND FIND_IN_SET(`course_id`, @cids);

-- ----------------------------------------------------------------------
-- 3. 防重复：待导入 code 已存在则先逻辑删除
-- ----------------------------------------------------------------------
UPDATE `training_course_category`
SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND `code` IN (
'A',
'A01',
'A0101',
'A0102',
'A02',
'A0201',
'A0202',
'A0203',
'A03',
'A0301',
'A0302',
'A0303',
'A0304',
'A04',
'A0401',
'A0402',
'A05',
'A0501',
'A0502',
'B',
'B01',
'B0101',
'B0102',
'B0103',
'B0104',
'B02',
'B0201',
'B0202',
'B0203',
'B03',
'B0301',
'B0302',
'B0303',
'B0304',
'B0305',
'B04',
'B0401',
'B0402',
'B0403',
'B0404',
'B05',
'B0501',
'B0502',
'B0503',
'B0504',
'B0505',
'B06',
'B0601',
'B0602',
'B07',
'B0701',
'B0702',
'B08',
'B0801',
'B0802',
'B0803',
'B0804',
'B0805',
'C',
'C01',
'C0101',
'C0102',
'C02',
'C0201',
'C0202',
'C0203',
'C0204',
'C03',
'C0301',
'C0302',
'C0303',
'C04',
'C0401',
'C0402',
'C0403',
'D',
'D01',
'D0101',
'D0102',
'D0103',
'D0104',
'D02',
'D0201',
'D0202',
'D0203',
'D03',
'D0301',
'D0302',
'D0303',
'D04',
'D0401',
'D0402',
'D0403',
'E',
'E01',
'E0101',
'E0102',
'E0103',
'E0104',
'E0105',
'E0106',
'E02',
'E0201',
'E0202',
'E0203',
'E0204',
'E0205',
'E03',
'E0301',
'E0302',
'E0303',
'E0304',
'E0305',
'E0306',
'E04',
'E0401',
'E0402',
'E0403',
'E0404',
'E0405',
'E05',
'E0501',
'E0502',
'E0503',
'F',
'F01',
'F0101',
'F0102',
'F0103',
'F02',
'F0201',
'F0202',
'G',
'G01',
'G0101',
'G0102',
'G02',
'G0201',
'G0202',
'G03',
'G0301',
'G0302',
'G0303',
'G0304',
'G0305',
'H',
'H01',
'H0101',
'H0102',
'H0103',
'H02',
'H0201',
'H0202'

);

-- ----------------------------------------------------------------------
-- 4. 导入权威课程分类字典（8 域 / 33 组 / 112 课）
-- ----------------------------------------------------------------------
INSERT INTO `training_course_category`
(`code`, `name`, `level`, `parent_id`, `biz_category`, `std_hours`, `is_required`, `sort`, `status`,
 `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
('A', '通用基础与安全', 1, 0, '全部（必修）', NULL, b'0', 1, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A01', '入职与公司认知', 2, 0, NULL, NULL, b'0', 2, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0101', '公司业务全景与产品线认知', 3, 0, NULL, 2, b'1', 3, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0102', '岗位职责与职业行为规范', 3, 0, NULL, 1, b'1', 4, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A02', 'HSE 基础', 2, 0, NULL, NULL, b'0', 5, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0201', '石油化工现场安全通识', 3, 0, NULL, 4, b'1', 6, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0202', '劳动防护用品正确使用', 3, 0, NULL, 1, b'1', 7, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0203', '职业健康与粉尘/溶剂危害防护', 3, 0, NULL, 2, b'1', 8, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A03', '特殊作业许可', 2, 0, NULL, NULL, b'0', 9, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0301', '受限空间作业（储罐/塔罐内部）', 3, 0, NULL, 4, b'1', 10, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0302', '高处作业与脚手架安全', 3, 0, NULL, 3, b'1', 11, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0303', '动火作业与防火防爆', 3, 0, NULL, 3, b'1', 12, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0304', '临时用电与工机具安全', 3, 0, NULL, 2, b'1', 13, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A04', '应急与事故', 2, 0, NULL, NULL, b'0', 14, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0401', '现场应急处置与逃生', 3, 0, NULL, 2, b'1', 15, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0402', '典型事故案例复盘', 3, 0, NULL, 2, b'1', 16, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A05', '质量与制度', 2, 0, NULL, NULL, b'0', 17, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0501', '质量管理体系与三检制', 3, 0, NULL, 2, b'1', 18, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('A0502', '施工资料与影像留证规范', 3, 0, NULL, 2, b'1', 19, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B', '防腐涂装', 1, 0, '一/二/三/八/十五/二十二~三十四/三十六/三十七', NULL, b'0', 20, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B01', '表面处理', 2, 0, NULL, NULL, b'0', 21, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0101', '除锈等级判定（Sa/St 标准）', 3, 0, NULL, 3, b'0', 22, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0102', '喷砂/抛丸设备操作与维护', 3, 0, NULL, 6, b'0', 23, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0103', '高压水射流除锈', 3, 0, NULL, 4, b'0', 24, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0104', '表面粗糙度与清洁度检测', 3, 0, NULL, 3, b'0', 25, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B02', '涂料与配比', 2, 0, NULL, NULL, b'0', 26, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0201', '防腐涂料体系认知（环氧/聚氨酯/无机锌）', 3, 0, NULL, 4, b'0', 27, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0202', '涂料调配、稀释与适用期控制', 3, 0, NULL, 3, b'0', 28, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0203', '环境温湿度露点控制', 3, 0, NULL, 2, b'0', 29, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B03', '涂装施工', 2, 0, NULL, NULL, b'0', 30, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0301', '高压无气喷涂操作', 3, 0, NULL, 8, b'0', 31, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0302', '刷涂/滚涂与复杂部位处理', 3, 0, NULL, 4, b'0', 32, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0303', '干膜/湿膜厚度控制与检测', 3, 0, NULL, 4, b'0', 33, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0304', '漏点（电火花）检测', 3, 0, NULL, 3, b'0', 34, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0305', '涂层缺陷识别与返修', 3, 0, NULL, 4, b'0', 35, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B04', '储罐防腐', 2, 0, NULL, NULL, b'0', 36, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0401', '净化罐/污水罐内防腐工艺', 3, 0, NULL, 6, b'0', 37, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0402', '油罐、沉降罐内外防腐', 3, 0, NULL, 6, b'0', 38, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0403', '塔罐及球罐防腐', 3, 0, NULL, 4, b'0', 39, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0404', '储罐内衬玻璃钢/陶瓷施工', 3, 0, NULL, 6, b'0', 40, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B05', '管线外防腐', 2, 0, NULL, NULL, b'0', 41, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0501', '3PE 三层聚乙烯外防腐工艺', 3, 0, NULL, 8, b'0', 42, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0502', '环氧粉末（FBE）熔结防腐', 3, 0, NULL, 6, b'0', 43, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0503', '地面管线防腐施工', 3, 0, NULL, 4, b'0', 44, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0504', '井下管线与注水油管防腐', 3, 0, NULL, 4, b'0', 45, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0505', '弯管、冷器管、加热炉盘管防腐', 3, 0, NULL, 4, b'0', 46, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B06', '补口补伤', 2, 0, NULL, NULL, b'0', 47, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0601', '热收缩套/带补口工艺', 3, 0, NULL, 6, b'0', 48, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0602', '补伤与搭接质量控制', 3, 0, NULL, 3, b'0', 49, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B07', '内涂', 2, 0, NULL, NULL, b'0', 50, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0701', '管道内涂工艺与设备', 3, 0, NULL, 6, b'0', 51, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0702', '内涂层质量检测', 3, 0, NULL, 3, b'0', 52, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B08', '特种防腐', 2, 0, NULL, NULL, b'0', 53, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0801', '环氧玻璃钢防腐', 3, 0, NULL, 4, b'0', 54, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0802', '环氧陶瓷/金属陶瓷防腐', 3, 0, NULL, 4, b'0', 55, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0803', '耐磨油管防腐', 3, 0, NULL, 4, b'0', 56, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0804', '井口与设备防腐', 3, 0, NULL, 4, b'0', 57, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('B0805', '防蜡防垢技术', 3, 0, NULL, 3, b'0', 58, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C', '保温绝热与保冷', 1, 0, '二/四/十六/三十三', NULL, b'0', 59, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C01', '保温基础', 2, 0, NULL, NULL, b'0', 60, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0101', '保温材料认知与选型', 3, 0, NULL, 3, b'0', 61, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0102', '保温结构层次与厚度计算', 3, 0, NULL, 4, b'0', 62, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C02', '保温施工', 2, 0, NULL, NULL, b'0', 63, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0201', '管道保温施工工艺', 3, 0, NULL, 6, b'0', 64, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0202', '设备/储罐保温施工', 3, 0, NULL, 6, b'0', 65, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0203', '铁皮/铝皮保护层制作与安装', 3, 0, NULL, 6, b'0', 66, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0204', '阀门、法兰等异形件保温', 3, 0, NULL, 4, b'0', 67, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C03', '防腐绝热保冷', 2, 0, NULL, NULL, b'0', 68, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0301', '保冷工艺与材料（聚氨酯/泡沫玻璃）', 3, 0, NULL, 6, b'0', 69, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0302', '防潮层与隔汽层施工', 3, 0, NULL, 4, b'0', 70, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0303', '低温装置保冷质量控制', 3, 0, NULL, 4, b'0', 71, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C04', '冬防与维护', 2, 0, NULL, NULL, b'0', 72, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0401', '冬防保温方案与实施', 3, 0, NULL, 4, b'0', 73, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0402', '电伴热与蒸汽伴热配合', 3, 0, NULL, 3, b'0', 74, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('C0403', '保温拆装恢复作业', 3, 0, NULL, 4, b'0', 75, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D', '内衬与管杆修复', 1, 0, '五/六/七/八/十三/十八/二十/二十一/三十五', NULL, b'0', 76, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D01', '内衬技术', 2, 0, NULL, NULL, b'0', 77, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0101', '管道内衬修复工艺总览', 3, 0, NULL, 4, b'0', 78, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0102', '高分子聚乙烯内衬施工', 3, 0, NULL, 6, b'0', 79, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0103', '非金属油管内衬', 3, 0, NULL, 6, b'0', 80, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0104', '内衬管选型与采购要点', 3, 0, NULL, 3, b'0', 81, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D02', '油管油杆修复', 2, 0, NULL, NULL, b'0', 82, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0201', '油管修复流程与检验', 3, 0, NULL, 6, b'0', 83, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0202', '油杆修复与校直', 3, 0, NULL, 6, b'0', 84, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0203', '摩擦焊修复油管接头接箍', 3, 0, NULL, 8, b'0', 85, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D03', '管道修复补强', 2, 0, NULL, NULL, b'0', 86, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0301', '管道缺陷评估与修复选型', 3, 0, NULL, 4, b'0', 87, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0302', '补强套筒/复合材料补强', 3, 0, NULL, 6, b'0', 88, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0303', '带压堵漏技术', 3, 0, NULL, 6, b'0', 89, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D04', '套管与装备', 2, 0, NULL, NULL, b'0', 90, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0401', '套管修复工艺', 3, 0, NULL, 4, b'0', 91, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0402', '套筒修复', 3, 0, NULL, 4, b'0', 92, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('D0403', '钻头及装备修复', 3, 0, NULL, 4, b'0', 93, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E', '检测与理化试验', 1, 0, '三十九~六十三', NULL, b'0', 94, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E01', '无损检测基础', 2, 0, NULL, NULL, b'0', 95, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0101', '无损检测方法总览与选用', 3, 0, NULL, 4, b'0', 96, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0102', '超声检测（UT）基础与实操', 3, 0, NULL, 12, b'0', 97, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0103', '磁粉检测（MT）', 3, 0, NULL, 8, b'0', 98, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0104', '渗透检测（PT）', 3, 0, NULL, 8, b'0', 99, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0105', '射线检测（RT）与辐射安全', 3, 0, NULL, 12, b'0', 100, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0106', '涡流/漏磁检测', 3, 0, NULL, 8, b'0', 101, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E02', '管材与管件检测', 2, 0, NULL, NULL, b'0', 102, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0201', '废油管检测与分级', 3, 0, NULL, 4, b'0', 103, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0202', '钻杆、钻具油套管检测', 3, 0, NULL, 6, b'0', 104, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0203', '高压管件检测', 3, 0, NULL, 4, b'0', 105, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0204', '压力管道检测', 3, 0, NULL, 8, b'0', 106, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0205', '工业管道与长输油管检测', 3, 0, NULL, 8, b'0', 107, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E03', '容器与设备检测', 2, 0, NULL, NULL, b'0', 108, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0301', '压力容器检测', 3, 0, NULL, 8, b'0', 109, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0302', '储罐检测（含罐底漏磁）', 3, 0, NULL, 6, b'0', 110, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0303', '球罐检测', 3, 0, NULL, 6, b'0', 111, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0304', '锅炉检测', 3, 0, NULL, 6, b'0', 112, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0305', '设备与设施检测', 3, 0, NULL, 4, b'0', 113, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0306', '机井架检测', 3, 0, NULL, 4, b'0', 114, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E04', '专项检测', 2, 0, NULL, NULL, b'0', 115, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0401', '气密性检测', 3, 0, NULL, 4, b'0', 116, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0402', '气体与固体检测', 3, 0, NULL, 4, b'0', 117, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0403', '防雷、防爆检测', 3, 0, NULL, 6, b'0', 118, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0404', '输电线路与变电站检测', 3, 0, NULL, 6, b'0', 119, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0405', '井下材料检测', 3, 0, NULL, 4, b'0', 120, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E05', '理化与计量', 2, 0, NULL, NULL, b'0', 121, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0501', '理化试验基础（力学/金相）', 3, 0, NULL, 8, b'0', 122, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0502', '计量器具检定与校准', 3, 0, NULL, 4, b'0', 123, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('E0503', '检测报告编制与数据判定', 3, 0, NULL, 4, b'0', 124, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('F', '材料与采购', 1, 0, '九/十四/三十五', NULL, b'0', 125, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('F01', '材料认知', 2, 0, NULL, NULL, b'0', 126, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('F0101', '防腐材料技术指标与验收', 3, 0, NULL, 4, b'0', 127, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('F0102', '天然气管材认知', 3, 0, NULL, 4, b'0', 128, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('F0103', '保温材料进场检验', 3, 0, NULL, 3, b'0', 129, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('F02', '采购与仓储', 2, 0, NULL, NULL, b'0', 130, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('F0201', '防腐材料采购流程与供方管理', 3, 0, NULL, 3, b'0', 131, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('F0202', '材料储存、保质期与领用管理', 3, 0, NULL, 3, b'0', 132, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G', '施工管理与项目交付', 1, 0, '十/十二/十九/三十八', NULL, b'0', 133, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G01', '工艺与方案', 2, 0, NULL, NULL, b'0', 134, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G0101', '施工方案编制与技术交底', 3, 0, NULL, 4, b'0', 135, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G0102', '工艺卡与作业指导书应用', 3, 0, NULL, 3, b'0', 136, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G02', '质量与验收', 2, 0, NULL, NULL, b'0', 137, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G0201', '防腐保温工程验收标准', 3, 0, NULL, 4, b'0', 138, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G0202', '不合格品处置与返工管理', 3, 0, NULL, 3, b'0', 139, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G03', '项目与班组', 2, 0, NULL, NULL, b'0', 140, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G0301', '班组日常管理与派工', 3, 0, NULL, 4, b'0', 141, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G0302', '工程量确认与结算基础', 3, 0, NULL, 4, b'0', 142, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G0303', '产能建设管线项目要点', 3, 0, NULL, 4, b'0', 143, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G0304', '天然气处理厂工程要点', 3, 0, NULL, 4, b'0', 144, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('G0305', '维修工程组织与管理', 3, 0, NULL, 4, b'0', 145, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('H', '数字化与平台工具', 1, 0, '十/十二', NULL, b'0', 146, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('H01', '平台操作', 2, 0, NULL, NULL, b'0', 147, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('H0101', 'MES 系统基础操作', 3, 0, NULL, 4, b'0', 148, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('H0102', '生产数据与报工录入规范', 3, 0, NULL, 3, b'0', 149, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('H0103', '施工影像与资料上传规范', 3, 0, NULL, 2, b'0', 150, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('H02', '数据与经营', 2, 0, NULL, NULL, b'0', 151, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('H0201', '报表看板阅读与使用', 3, 0, NULL, 2, b'0', 152, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1),
('H0202', '招投标信息识别与响应基础', 3, 0, NULL, 3, b'0', 153, 'active', '云培训学院课程分类字典', 'seed', NOW(), 'admin', NOW(), b'0', 1);

-- ----------------------------------------------------------------------
-- 5. 按编码前缀回填父级 parent_id（组=域前1位；课=组前3位）
-- ----------------------------------------------------------------------
UPDATE `training_course_category` t
JOIN `training_course_category` p ON p.`deleted` = b'0'
    AND p.`code` = CASE
        WHEN t.`level` = 3 THEN LEFT(t.`code`, 3)
        WHEN t.`level` = 2 THEN LEFT(t.`code`, 1)
        ELSE NULL END
SET t.`parent_id` = p.`id`, t.`updater` = 'admin', t.`update_time` = NOW()
WHERE t.`deleted` = b'0' AND t.`level` IN (2, 3);

-- ----------------------------------------------------------------------
-- 6. 校验（期望：域8 组33 课112；孤立分类 0）
-- ----------------------------------------------------------------------
SELECT `level`, COUNT(*) AS cnt FROM `training_course_category`
WHERE `deleted` = b'0' GROUP BY `level` ORDER BY `level`;
SELECT COUNT(*) AS orphan_cnt FROM `training_course_category` t
WHERE t.`deleted` = b'0' AND t.`level` > 1 AND NOT EXISTS (
    SELECT 1 FROM `training_course_category` p WHERE p.`id` = t.`parent_id` AND p.`deleted` = b'0');
