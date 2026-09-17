-- 岗位工种标签导入（来源：云培训学院-岗位工种表(1).xlsx，共 56 个工种，5 个序列）
-- 同时软删除 process/safety 维度旧标签与旧 work_type 占位数据
SET NAMES utf8mb4;

-- 1. 清理：软删除 安全/工艺 维度全部标签
UPDATE training_tag SET deleted = 1, updater = 'admin', update_time = NOW()
WHERE deleted = 0 AND dimension IN ('safety', 'process');

-- 2. 清理：软删除 work_type 维度旧占位标签（焊工/电工/管道工）
UPDATE training_tag SET deleted = 1, updater = 'admin', update_time = NOW()
WHERE deleted = 0 AND dimension = 'work_type';

INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '表面处理工（喷砂/抛丸）', 'work_type', 1, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='表面处理工（喷砂/抛丸）' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '防腐涂装工', 'work_type', 2, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='防腐涂装工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '储罐防腐工', 'work_type', 3, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='储罐防腐工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '3PE/粉末防腐工', 'work_type', 4, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='3PE/粉末防腐工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '补口补伤工', 'work_type', 5, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='补口补伤工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '内涂施工工', 'work_type', 6, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='内涂施工工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '特种防腐工（玻璃钢/陶瓷）', 'work_type', 7, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='特种防腐工（玻璃钢/陶瓷）' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '保温工', 'work_type', 8, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='保温工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '保冷工（绝热施工）', 'work_type', 9, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='保冷工（绝热施工）' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '铁皮加工安装工', 'work_type', 10, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='铁皮加工安装工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '冬防保温工', 'work_type', 11, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='冬防保温工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '内衬施工工', 'work_type', 12, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='内衬施工工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '油管修复工', 'work_type', 13, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='油管修复工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '油杆修复工', 'work_type', 14, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='油杆修复工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '摩擦焊操作工', 'work_type', 15, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='摩擦焊操作工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '管道修复补强工', 'work_type', 16, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='管道修复补强工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '套管修复工', 'work_type', 17, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='套管修复工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '装备钻头修复工', 'work_type', 18, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='装备钻头修复工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '井口作业防腐工', 'work_type', 19, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='井口作业防腐工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '防蜡防垢作业工', 'work_type', 20, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='防蜡防垢作业工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '管线安装工', 'work_type', 21, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='管线安装工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '起重吊装指挥/司索', 'work_type', 22, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='起重吊装指挥/司索' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '电焊工', 'work_type', 23, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='电焊工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '电工（维修电工）', 'work_type', 24, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='电工（维修电工）' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '架子工', 'work_type', 25, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='架子工' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '防腐工艺技术员', 'work_type', 26, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='防腐工艺技术员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '保温绝热技术员', 'work_type', 27, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='保温绝热技术员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '内衬修复技术员', 'work_type', 28, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='内衬修复技术员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '材料工程师', 'work_type', 29, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='材料工程师' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '焊接工艺技术员', 'work_type', 30, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='焊接工艺技术员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '标准与资料员', 'work_type', 31, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='标准与资料员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '防腐涂层检验员', 'work_type', 32, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='防腐涂层检验员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '保温工程检验员', 'work_type', 33, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='保温工程检验员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '超声检测员（UT）', 'work_type', 34, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='超声检测员（UT）' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '射线检测员（RT）', 'work_type', 35, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='射线检测员（RT）' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '磁粉/渗透检测员（MT/PT）', 'work_type', 36, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='磁粉/渗透检测员（MT/PT）' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '涡流/漏磁检测员', 'work_type', 37, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='涡流/漏磁检测员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '管材管件检测员', 'work_type', 38, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='管材管件检测员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '压力容器/管道检验员', 'work_type', 39, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='压力容器/管道检验员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '储罐检验员', 'work_type', 40, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='储罐检验员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '电气防雷防爆检测员', 'work_type', 41, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='电气防雷防爆检测员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '气密性与气体检测员', 'work_type', 42, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='气密性与气体检测员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '理化试验员', 'work_type', 43, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='理化试验员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '计量检定员', 'work_type', 44, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='计量检定员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '设施设备检测员', 'work_type', 45, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='设施设备检测员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '班组长', 'work_type', 46, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='班组长' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '工段长/施工负责人', 'work_type', 47, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='工段长/施工负责人' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '项目经理', 'work_type', 48, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='项目经理' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '生产调度', 'work_type', 49, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='生产调度' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '培训师/内部讲师', 'work_type', 50, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='培训师/内部讲师' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT 'HSE安全员', 'work_type', 51, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='HSE安全员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '设备管理员', 'work_type', 52, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='设备管理员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '材料采购员', 'work_type', 53, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='材料采购员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '仓储管理员', 'work_type', 54, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='仓储管理员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '数字化/MES专员', 'work_type', 55, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='数字化/MES专员' AND deleted=0) e);
INSERT INTO `training_tag` (`name`, `dimension`, `sort`, `creator`)
SELECT '商务与投标专员', 'work_type', 56, 'admin' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM training_tag WHERE dimension='work_type' AND name='商务与投标专员' AND deleted=0) e);
