-- =============================================================
-- 云培训学院 · 工种标签种子数据（work_type 维度，56 个工种）
-- 用途：随机组卷「按工种范围抽题」依赖本维度标签；新环境/组员本地库执行本脚本即可获得完整工种体系。
-- 幂等：按 (dimension, name) 防重，可重复执行。
-- 执行后：在仓库 scripts/auto-tag-worktype.mjs 运行一次，即可按题干关键词为现有题目批量打上工种标签。
-- =============================================================
USE `ruoyi-vue-pro`;

INSERT INTO training_tag (name, dimension, sort, creator, updater, tenant_id)
SELECT v.name, 'work_type', v.sort, '1', '1', 1 FROM (
  SELECT '表面处理工（喷砂/抛丸）' AS name, 1 AS sort UNION ALL
  SELECT '防腐涂装工', 2 UNION ALL
  SELECT '储罐防腐工', 3 UNION ALL
  SELECT '3PE/粉末防腐工', 4 UNION ALL
  SELECT '补口补伤工', 5 UNION ALL
  SELECT '内涂施工工', 6 UNION ALL
  SELECT '特种防腐工（玻璃钢/陶瓷）', 7 UNION ALL
  SELECT '保温工', 8 UNION ALL
  SELECT '保冷工（绝热施工）', 9 UNION ALL
  SELECT '铁皮加工安装工', 10 UNION ALL
  SELECT '冬防保温工', 11 UNION ALL
  SELECT '内衬施工工', 12 UNION ALL
  SELECT '油管修复工', 13 UNION ALL
  SELECT '油杆修复工', 14 UNION ALL
  SELECT '摩擦焊操作工', 15 UNION ALL
  SELECT '管道修复补强工', 16 UNION ALL
  SELECT '套管修复工', 17 UNION ALL
  SELECT '装备钻头修复工', 18 UNION ALL
  SELECT '井口作业防腐工', 19 UNION ALL
  SELECT '防蜡防垢作业工', 20 UNION ALL
  SELECT '管线安装工', 21 UNION ALL
  SELECT '起重吊装指挥/司索', 22 UNION ALL
  SELECT '电焊工', 23 UNION ALL
  SELECT '电工（维修电工）', 24 UNION ALL
  SELECT '架子工', 25 UNION ALL
  SELECT '防腐工艺技术员', 26 UNION ALL
  SELECT '保温绝热技术员', 27 UNION ALL
  SELECT '内衬修复技术员', 28 UNION ALL
  SELECT '材料工程师', 29 UNION ALL
  SELECT '焊接工艺技术员', 30 UNION ALL
  SELECT '标准与资料员', 31 UNION ALL
  SELECT '防腐涂层检验员', 32 UNION ALL
  SELECT '保温工程检验员', 33 UNION ALL
  SELECT '超声检测员（UT）', 34 UNION ALL
  SELECT '射线检测员（RT）', 35 UNION ALL
  SELECT '磁粉/渗透检测员（MT/PT）', 36 UNION ALL
  SELECT '涡流/漏磁检测员', 37 UNION ALL
  SELECT '管材管件检测员', 38 UNION ALL
  SELECT '压力容器/管道检验员', 39 UNION ALL
  SELECT '储罐检验员', 40 UNION ALL
  SELECT '电气防雷防爆检测员', 41 UNION ALL
  SELECT '气密性与气体检测员', 42 UNION ALL
  SELECT '理化试验员', 43 UNION ALL
  SELECT '计量检定员', 44 UNION ALL
  SELECT '设施设备检测员', 45 UNION ALL
  SELECT '班组长', 46 UNION ALL
  SELECT '工段长/施工负责人', 47 UNION ALL
  SELECT '项目经理', 48 UNION ALL
  SELECT '生产调度', 49 UNION ALL
  SELECT '培训师/内部讲师', 50 UNION ALL
  SELECT 'HSE安全员', 51 UNION ALL
  SELECT '设备管理员', 52 UNION ALL
  SELECT '材料采购员', 53 UNION ALL
  SELECT '仓储管理员', 54 UNION ALL
  SELECT '数字化/MES专员', 55 UNION ALL
  SELECT '商务与投标专员', 56
) AS v
WHERE NOT EXISTS (
  SELECT 1 FROM training_tag t
  WHERE t.dimension = 'work_type' AND t.name = v.name AND t.deleted = 0
);

-- 验证
SELECT COUNT(*) AS work_type_tags FROM training_tag WHERE dimension = 'work_type' AND deleted = 0;
