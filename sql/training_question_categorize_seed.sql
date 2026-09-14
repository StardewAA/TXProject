-- ============================================================
-- 考试题库 · 演示题库 35 道存量试题批量归类
-- 适用范围：按种子脚本顺序初始化的演示题库（training_question id 1~35）
-- 归类依据：按题干内容映射到「试题分类」现有分类树叶子节点
-- 幂等性：UPDATE 按主键定位，重复执行结果一致
-- 执行方式：mysql --default-character-set=utf8mb4 ruoyi-vue-pro < training_question_categorize_seed.sql
--
-- 分类对照：
--   570 公司业务全景与产品线认知（入职与公司认知）
--   571 岗位职责与职业行为规范（入职与公司认知）
--   608 管线外防腐 / 609 3PE 工艺 / 610 FBE（防腐涂装）
--   627 保温基础 / 628 保温材料认知与选型 / 631 管道保温施工工艺（保温绝热与保冷）
--   701 工艺与方案 / 707 项目与班组 / 709 工程量确认与结算基础（施工管理与项目交付）
-- ============================================================
SET NAMES utf8mb4;

-- 公司业务全景与产品线认知：服务对象、一体化方案、产品与服务、业务链条、防腐/保温概念
UPDATE training_question SET category_id = 570, update_time = NOW()
WHERE deleted = 0 AND id IN (1,6,7,9,10,11,16,20,21,22,26,27,28);

-- 岗位职责与职业行为规范：现场人员岗位认知
UPDATE training_question SET category_id = 571, update_time = NOW()
WHERE deleted = 0 AND id IN (30);

-- 管线外防腐：外防腐主流工艺
UPDATE training_question SET category_id = 608, update_time = NOW()
WHERE deleted = 0 AND id IN (2);

-- 3PE 三层聚乙烯外防腐工艺：PE 含义、三层结构、与刷漆区别
UPDATE training_question SET category_id = 609, update_time = NOW()
WHERE deleted = 0 AND id IN (8,17,34);

-- 环氧粉末（FBE）熔结防腐：FBE 应用部位与缩写
UPDATE training_question SET category_id = 610, update_time = NOW()
WHERE deleted = 0 AND id IN (5,19);

-- 保温基础：保温目的、应用部位、防冻凝
UPDATE training_question SET category_id = 627, update_time = NOW()
WHERE deleted = 0 AND id IN (3,13,23);

-- 保温材料认知与选型：绝热材料分类、选型与工况匹配、岩棉误用案例
UPDATE training_question SET category_id = 628, update_time = NOW()
WHERE deleted = 0 AND id IN (4,14,18,33);

-- 管道保温施工工艺：蒸汽管道烫伤分析、保温工程开工前信息
UPDATE training_question SET category_id = 631, update_time = NOW()
WHERE deleted = 0 AND id IN (31,32);

-- 工艺与方案：防腐方案考虑因素、行业与方案、现场勘查定方案
UPDATE training_question SET category_id = 701, update_time = NOW()
WHERE deleted = 0 AND id IN (12,24,25);

-- 工程量确认与结算基础：项目洽谈与承接确认
UPDATE training_question SET category_id = 709, update_time = NOW()
WHERE deleted = 0 AND id IN (15,29);

-- 项目与班组：供热工程工期组织案例
UPDATE training_question SET category_id = 707, update_time = NOW()
WHERE deleted = 0 AND id IN (35);
