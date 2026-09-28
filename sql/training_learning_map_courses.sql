-- ============================================================
-- 云培训学院 · 学习地图课程种子数据
-- 来源：《云培训学院-学习地图示例(1).xlsx》（213 行工种×课程映射）
-- 去重后共 78 门课程，按课程编号排序
-- 说明：
--   1. status = 0（草稿）：课程仅有名称与定位规划，媒资内容后续制作，走正常上架流程
--   2. course_level 存学习阶段（L1-L2/L2-L3/L3-L4/L4-L5）
--   3. applicable_posts 汇总该课程覆盖的全部工种
--   4. 幂等：按 code 判重，重复执行不会产生重复数据
-- 执行方式：mysql --default-character-set=utf8mb4 ruoyi-vue-pro < training_learning_map_courses.sql
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO training_course
  (code, title, course_level, applicable_posts, is_required, media_type, status, version, is_current, sort, tenant_id, creator)
SELECT v.code, v.title, v.course_level, v.applicable_posts, v.is_required, 0, 0, 1, b'1', v.sort, 1, 'learning-map-seed'
FROM (
  SELECT 'A0101' AS code, '公司业务全景与产品线认知' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 1 AS sort
UNION ALL
  SELECT 'A0102' AS code, '岗位职责与职业行为规范' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 2 AS sort
UNION ALL
  SELECT 'A0201' AS code, '石油化工现场安全通识' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 3 AS sort
UNION ALL
  SELECT 'A0202' AS code, '劳动防护用品正确使用' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 4 AS sort
UNION ALL
  SELECT 'A0203' AS code, '职业健康与粉尘/溶剂危害防护' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 5 AS sort
UNION ALL
  SELECT 'A0301' AS code, '受限空间作业（储罐/塔罐内部）' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 6 AS sort
UNION ALL
  SELECT 'A0302' AS code, '高处作业与脚手架安全' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 7 AS sort
UNION ALL
  SELECT 'A0303' AS code, '动火作业与防火防爆' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 8 AS sort
UNION ALL
  SELECT 'A0304' AS code, '临时用电与工机具安全' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 9 AS sort
UNION ALL
  SELECT 'A0401' AS code, '现场应急处置与逃生' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 10 AS sort
UNION ALL
  SELECT 'A0402' AS code, '典型事故案例复盘' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 11 AS sort
UNION ALL
  SELECT 'A0501' AS code, '质量管理体系与三检制' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 12 AS sort
UNION ALL
  SELECT 'A0502' AS code, '施工资料与影像留证规范' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 13 AS sort
UNION ALL
  SELECT 'B0101' AS code, '除锈等级判定（Sa/St 标准）' AS title, '' AS course_level, '防腐涂装工、储罐防腐工、保温工' AS applicable_posts, 1 AS is_required, 14 AS sort
UNION ALL
  SELECT 'B0102' AS code, '喷砂/抛丸设备操作与维护' AS title, 'L2-L3' AS course_level, '防腐涂装工、储罐防腐工' AS applicable_posts, 1 AS is_required, 15 AS sort
UNION ALL
  SELECT 'B0201' AS code, '防腐涂料体系认知（环氧/聚氨酯/无机锌）' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工' AS applicable_posts, 1 AS is_required, 16 AS sort
UNION ALL
  SELECT 'B0202' AS code, '涂料调配、稀释与适用期控制' AS title, 'L2-L3' AS course_level, '防腐涂装工' AS applicable_posts, 1 AS is_required, 17 AS sort
UNION ALL
  SELECT 'B0203' AS code, '环境温湿度露点控制' AS title, 'L1-L2' AS course_level, '防腐涂装工、储罐防腐工' AS applicable_posts, 1 AS is_required, 18 AS sort
UNION ALL
  SELECT 'B0301' AS code, '高压无气喷涂操作' AS title, 'L2-L3' AS course_level, '防腐涂装工、储罐防腐工' AS applicable_posts, 1 AS is_required, 19 AS sort
UNION ALL
  SELECT 'B0302' AS code, '刷涂/滚涂与复杂部位处理' AS title, 'L1-L2' AS course_level, '防腐涂装工' AS applicable_posts, 1 AS is_required, 20 AS sort
UNION ALL
  SELECT 'B0303' AS code, '干膜/湿膜厚度控制与检测' AS title, 'L2-L3' AS course_level, '防腐涂装工、储罐防腐工' AS applicable_posts, 1 AS is_required, 21 AS sort
UNION ALL
  SELECT 'B0304' AS code, '漏点（电火花）检测' AS title, 'L2-L3' AS course_level, '防腐涂装工、储罐防腐工' AS applicable_posts, 1 AS is_required, 22 AS sort
UNION ALL
  SELECT 'B0305' AS code, '涂层缺陷识别与返修' AS title, 'L3-L4' AS course_level, '防腐涂装工、储罐防腐工' AS applicable_posts, 1 AS is_required, 23 AS sort
UNION ALL
  SELECT 'B0401' AS code, '净化罐/污水罐内防腐工艺' AS title, 'L2-L3' AS course_level, '储罐防腐工' AS applicable_posts, 1 AS is_required, 24 AS sort
UNION ALL
  SELECT 'B0402' AS code, '油罐、沉降罐内外防腐' AS title, 'L2-L3' AS course_level, '储罐防腐工' AS applicable_posts, 1 AS is_required, 25 AS sort
UNION ALL
  SELECT 'B0403' AS code, '塔罐及球罐防腐' AS title, 'L3-L4' AS course_level, '储罐防腐工' AS applicable_posts, 1 AS is_required, 26 AS sort
UNION ALL
  SELECT 'B0404' AS code, '储罐内衬玻璃钢/陶瓷施工' AS title, 'L3-L4' AS course_level, '储罐防腐工、内衬施工工' AS applicable_posts, 1 AS is_required, 27 AS sort
UNION ALL
  SELECT 'B0501' AS code, '3PE 三层聚乙烯外防腐工艺' AS title, 'L3-L4' AS course_level, '防腐涂装工' AS applicable_posts, 1 AS is_required, 28 AS sort
UNION ALL
  SELECT 'B0502' AS code, '环氧粉末（FBE）熔结防腐' AS title, 'L3-L4' AS course_level, '防腐涂装工' AS applicable_posts, 1 AS is_required, 29 AS sort
UNION ALL
  SELECT 'B0503' AS code, '地面管线防腐施工' AS title, 'L2-L3' AS course_level, '防腐涂装工' AS applicable_posts, 1 AS is_required, 30 AS sort
UNION ALL
  SELECT 'B0601' AS code, '热收缩套/带补口工艺' AS title, 'L3-L4' AS course_level, '防腐涂装工' AS applicable_posts, 1 AS is_required, 31 AS sort
UNION ALL
  SELECT 'B0701' AS code, '管道内涂工艺与设备' AS title, 'L3-L4' AS course_level, '防腐涂装工' AS applicable_posts, 1 AS is_required, 32 AS sort
UNION ALL
  SELECT 'B0801' AS code, '环氧玻璃钢防腐' AS title, 'L3-L4' AS course_level, '储罐防腐工' AS applicable_posts, 1 AS is_required, 33 AS sort
UNION ALL
  SELECT 'C0101' AS code, '保温材料认知与选型' AS title, 'L1-L2' AS course_level, '保温工、保冷工（绝热施工）' AS applicable_posts, 1 AS is_required, 34 AS sort
UNION ALL
  SELECT 'C0102' AS code, '保温结构层次与厚度计算' AS title, 'L2-L3' AS course_level, '保温工、保冷工（绝热施工）' AS applicable_posts, 1 AS is_required, 35 AS sort
UNION ALL
  SELECT 'C0201' AS code, '管道保温施工工艺' AS title, '' AS course_level, '防腐涂装工、保温工、保冷工（绝热施工）' AS applicable_posts, 1 AS is_required, 36 AS sort
UNION ALL
  SELECT 'C0202' AS code, '设备/储罐保温施工' AS title, 'L2-L3' AS course_level, '保温工' AS applicable_posts, 1 AS is_required, 37 AS sort
UNION ALL
  SELECT 'C0203' AS code, '铁皮/铝皮保护层制作与安装' AS title, 'L2-L3' AS course_level, '保温工、保冷工（绝热施工）' AS applicable_posts, 1 AS is_required, 38 AS sort
UNION ALL
  SELECT 'C0204' AS code, '阀门、法兰等异形件保温' AS title, 'L3-L4' AS course_level, '保温工、保冷工（绝热施工）' AS applicable_posts, 1 AS is_required, 39 AS sort
UNION ALL
  SELECT 'C0301' AS code, '保冷工艺与材料（聚氨酯/泡沫玻璃）' AS title, 'L1-L2' AS course_level, '保冷工（绝热施工）' AS applicable_posts, 1 AS is_required, 40 AS sort
UNION ALL
  SELECT 'C0302' AS code, '防潮层与隔汽层施工' AS title, 'L2-L3' AS course_level, '保冷工（绝热施工）' AS applicable_posts, 1 AS is_required, 41 AS sort
UNION ALL
  SELECT 'C0303' AS code, '低温装置保冷质量控制' AS title, 'L3-L4' AS course_level, '保冷工（绝热施工）' AS applicable_posts, 1 AS is_required, 42 AS sort
UNION ALL
  SELECT 'C0401' AS code, '冬防保温方案与实施' AS title, 'L3-L4' AS course_level, '保温工' AS applicable_posts, 1 AS is_required, 43 AS sort
UNION ALL
  SELECT 'C0402' AS code, '电伴热与蒸汽伴热配合' AS title, 'L3-L4' AS course_level, '保温工' AS applicable_posts, 1 AS is_required, 44 AS sort
UNION ALL
  SELECT 'C0403' AS code, '保温拆装恢复作业' AS title, 'L3-L4' AS course_level, '保温工、保冷工（绝热施工）' AS applicable_posts, 1 AS is_required, 45 AS sort
UNION ALL
  SELECT 'D0101' AS code, '管道内衬修复工艺总览' AS title, 'L1-L2' AS course_level, '内衬施工工' AS applicable_posts, 1 AS is_required, 46 AS sort
UNION ALL
  SELECT 'D0102' AS code, '高分子聚乙烯内衬施工' AS title, 'L2-L3' AS course_level, '内衬施工工' AS applicable_posts, 1 AS is_required, 47 AS sort
UNION ALL
  SELECT 'D0103' AS code, '非金属油管内衬' AS title, 'L2-L3' AS course_level, '内衬施工工' AS applicable_posts, 1 AS is_required, 48 AS sort
UNION ALL
  SELECT 'D0104' AS code, '内衬管选型与采购要点' AS title, 'L1-L2' AS course_level, '内衬施工工' AS applicable_posts, 1 AS is_required, 49 AS sort
UNION ALL
  SELECT 'D0301' AS code, '管道缺陷评估与修复选型' AS title, 'L2-L3' AS course_level, '内衬施工工' AS applicable_posts, 1 AS is_required, 50 AS sort
UNION ALL
  SELECT 'D0302' AS code, '补强套筒/复合材料补强' AS title, 'L3-L4' AS course_level, '内衬施工工' AS applicable_posts, 1 AS is_required, 51 AS sort
UNION ALL
  SELECT 'D0303' AS code, '带压堵漏技术' AS title, 'L3-L4' AS course_level, '内衬施工工' AS applicable_posts, 1 AS is_required, 52 AS sort
UNION ALL
  SELECT 'E0101' AS code, '无损检测方法总览与选用' AS title, '' AS course_level, '防腐涂装工、超声检测员（UT）' AS applicable_posts, 1 AS is_required, 53 AS sort
UNION ALL
  SELECT 'E0102' AS code, '超声检测（UT）基础与实操' AS title, 'L2-L3' AS course_level, '超声检测员（UT）' AS applicable_posts, 1 AS is_required, 54 AS sort
UNION ALL
  SELECT 'E0106' AS code, '涡流/漏磁检测' AS title, 'L3-L4' AS course_level, '超声检测员（UT）' AS applicable_posts, 1 AS is_required, 55 AS sort
UNION ALL
  SELECT 'E0201' AS code, '废油管检测与分级' AS title, 'L3-L4' AS course_level, '内衬施工工、超声检测员（UT）' AS applicable_posts, 1 AS is_required, 56 AS sort
UNION ALL
  SELECT 'E0202' AS code, '钻杆、钻具油套管检测' AS title, 'L3-L4' AS course_level, '超声检测员（UT）' AS applicable_posts, 1 AS is_required, 57 AS sort
UNION ALL
  SELECT 'E0204' AS code, '压力管道检测' AS title, 'L3-L4' AS course_level, '超声检测员（UT）' AS applicable_posts, 1 AS is_required, 58 AS sort
UNION ALL
  SELECT 'E0205' AS code, '工业管道与长输油管检测' AS title, 'L3-L4' AS course_level, '超声检测员（UT）' AS applicable_posts, 1 AS is_required, 59 AS sort
UNION ALL
  SELECT 'E0301' AS code, '压力容器检测' AS title, 'L4-L5' AS course_level, '超声检测员（UT）' AS applicable_posts, 1 AS is_required, 60 AS sort
UNION ALL
  SELECT 'E0302' AS code, '储罐检测（含罐底漏磁）' AS title, '' AS course_level, '储罐防腐工、超声检测员（UT）' AS applicable_posts, 1 AS is_required, 61 AS sort
UNION ALL
  SELECT 'E0503' AS code, '检测报告编制与数据判定' AS title, 'L2-L3' AS course_level, '超声检测员（UT）' AS applicable_posts, 1 AS is_required, 62 AS sort
UNION ALL
  SELECT 'F0103' AS code, '保温材料进场检验' AS title, '' AS course_level, '保温工、保冷工（绝热施工）' AS applicable_posts, 1 AS is_required, 63 AS sort
UNION ALL
  SELECT 'F0201' AS code, '防腐材料采购流程与供方管理' AS title, 'L4-L5' AS course_level, '班组长' AS applicable_posts, 1 AS is_required, 64 AS sort
UNION ALL
  SELECT 'G0101' AS code, '施工方案编制与技术交底' AS title, '' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、班组长' AS applicable_posts, 1 AS is_required, 65 AS sort
UNION ALL
  SELECT 'G0102' AS code, '工艺卡与作业指导书应用' AS title, '' AS course_level, '防腐涂装工、班组长' AS applicable_posts, 1 AS is_required, 66 AS sort
UNION ALL
  SELECT 'G0201' AS code, '防腐保温工程验收标准' AS title, '' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 67 AS sort
UNION ALL
  SELECT 'G0202' AS code, '不合格品处置与返工管理' AS title, '' AS course_level, '储罐防腐工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 68 AS sort
UNION ALL
  SELECT 'G0301' AS code, '班组日常管理与派工' AS title, '' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、班组长' AS applicable_posts, 1 AS is_required, 69 AS sort
UNION ALL
  SELECT 'G0302' AS code, '工程量确认与结算基础' AS title, 'L3-L4' AS course_level, '班组长' AS applicable_posts, 1 AS is_required, 70 AS sort
UNION ALL
  SELECT 'G0303' AS code, '产能建设管线项目要点' AS title, 'L4-L5' AS course_level, '班组长' AS applicable_posts, 1 AS is_required, 71 AS sort
UNION ALL
  SELECT 'G0304' AS code, '天然气处理厂工程要点' AS title, 'L4-L5' AS course_level, '班组长' AS applicable_posts, 1 AS is_required, 72 AS sort
UNION ALL
  SELECT 'G0305' AS code, '维修工程组织与管理' AS title, 'L4-L5' AS course_level, '班组长' AS applicable_posts, 1 AS is_required, 73 AS sort
UNION ALL
  SELECT 'H0101' AS code, 'MES 系统基础操作' AS title, '' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 74 AS sort
UNION ALL
  SELECT 'H0102' AS code, '生产数据与报工录入规范' AS title, '' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、班组长' AS applicable_posts, 1 AS is_required, 75 AS sort
UNION ALL
  SELECT 'H0103' AS code, '施工影像与资料上传规范' AS title, 'L3-L4' AS course_level, '班组长' AS applicable_posts, 1 AS is_required, 76 AS sort
UNION ALL
  SELECT 'H0201' AS code, '报表看板阅读与使用' AS title, '' AS course_level, '防腐涂装工、储罐防腐工、保温工、保冷工（绝热施工）、内衬施工工、超声检测员（UT）、班组长' AS applicable_posts, 1 AS is_required, 77 AS sort
UNION ALL
  SELECT 'H0202' AS code, '招投标信息识别与响应基础' AS title, 'L4-L5' AS course_level, '班组长' AS applicable_posts, 1 AS is_required, 78 AS sort
) v
WHERE NOT EXISTS (
  SELECT 1 FROM training_course tc WHERE tc.code = v.code AND tc.deleted = b'0' AND tc.tenant_id = 1
);
