-- 安全环保检测菜单瘦身：29 个平铺叶子 → 6 个子目录
-- 只动层级（parent_id/sort）与新增的 6 个目录行。页面组件、接口、数据、权限码一律不动。
--
-- ⚠️ 副作用：叶子被嵌一层后，vben 按 `parent/child` 拼路由
--    （packages/utils/src/helpers/generate-menus.ts:148），
--    所以 URL 会从 /safetyEnv/xxx 变成 /safetyEnv/<组>/xxx。收藏夹里的旧地址会失效。
--
-- 跑法：MYSQL_PWD=root mysql -uroot --default-character-set=utf8mb4 < 本文件
SET NAMES utf8mb4;

-- ============ 1. 6 个新目录（34010-34015 段此前未占用） ============
DELETE FROM system_menu WHERE id BETWEEN 34010 AND 34015;

INSERT INTO system_menu
  (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
  (34010, '污染管控',       '', 1, 10, 34000, 'pollution',         'ep:filter',           '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34011, '危废与化学品',   '', 1, 20, 34000, 'hazchem',           'ep:box',              '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34012, '排放监测',       '', 1, 30, 34000, 'emission',          'ep:monitor',          '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34013, '常规检测',       '', 1, 40, 34000, 'inspection',        'ep:document-checked', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34014, '治污设施与报告', '', 1, 50, 34000, 'facility-report',   'ep:set-up',           '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (34015, '应急管理',       '', 1, 60, 34000, 'emergency-section', 'ep:first-aid-kit',    '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- ============ 2. 叶子归位（组内 sort 重排为 1..n，按业务先后读起来顺） ============
UPDATE system_menu SET parent_id = 34010, sort = 1 WHERE id = 34301; -- 污染判定
UPDATE system_menu SET parent_id = 34010, sort = 2 WHERE id = 34302; -- 污染暂存台账
UPDATE system_menu SET parent_id = 34010, sort = 3 WHERE id = 34370; -- 污染追溯
UPDATE system_menu SET parent_id = 34010, sort = 4 WHERE id = 34380; -- 排放合规流水
UPDATE system_menu SET parent_id = 34010, sort = 5 WHERE id = 34350; -- 排污许可证

UPDATE system_menu SET parent_id = 34011, sort = 1 WHERE id = 34390; -- 危废管理
UPDATE system_menu SET parent_id = 34011, sort = 2 WHERE id = 34396; -- 危化品档案

UPDATE system_menu SET parent_id = 34012, sort = 1 WHERE id = 34410; -- 排放口
UPDATE system_menu SET parent_id = 34012, sort = 2 WHERE id = 34420; -- 废气监测记录
UPDATE system_menu SET parent_id = 34012, sort = 3 WHERE id = 34430; -- 废水监测记录
UPDATE system_menu SET parent_id = 34012, sort = 4 WHERE id = 34440; -- 碳排放核算

UPDATE system_menu SET parent_id = 34013, sort =  1 WHERE id = 34600; -- 粉尘检测记录
UPDATE system_menu SET parent_id = 34013, sort =  2 WHERE id = 34610; -- 噪声检测记录
UPDATE system_menu SET parent_id = 34013, sort =  3 WHERE id = 34620; -- 气体检测记录
UPDATE system_menu SET parent_id = 34013, sort =  4 WHERE id = 34630; -- 电气安全检查
UPDATE system_menu SET parent_id = 34013, sort =  5 WHERE id = 34640; -- 消防检查记录
UPDATE system_menu SET parent_id = 34013, sort =  6 WHERE id = 34650; -- 劳保用品检查
UPDATE system_menu SET parent_id = 34013, sort =  7 WHERE id = 34660; -- 压力容器检查
UPDATE system_menu SET parent_id = 34013, sort =  8 WHERE id = 34670; -- 职业危害检测
UPDATE system_menu SET parent_id = 34013, sort =  9 WHERE id = 34680; -- 检测计划
UPDATE system_menu SET parent_id = 34013, sort = 10 WHERE id = 34690; -- 检测标准

UPDATE system_menu SET parent_id = 34014, sort = 1 WHERE id = 34463; -- 治污设施
UPDATE system_menu SET parent_id = 34014, sort = 2 WHERE id = 34400; -- 环保检测报告
UPDATE system_menu SET parent_id = 34014, sort = 3 WHERE id = 34700; -- 环保看板

UPDATE system_menu SET parent_id = 34015, sort = 1 WHERE id = 34500; -- 应急预案
UPDATE system_menu SET parent_id = 34015, sort = 2 WHERE id = 34510; -- 应急物资
UPDATE system_menu SET parent_id = 34015, sort = 3 WHERE id = 34520; -- 应急演练
UPDATE system_menu SET parent_id = 34015, sort = 4 WHERE id = 34530; -- 应急事件
UPDATE system_menu SET parent_id = 34015, sort = 5 WHERE id = 34540; -- 应急处置卡

-- ============ 3. 新目录授权 ============
-- 只授权给「组内确实有叶子」的角色：否则某些角色会看到一个点进去是空的目录。
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT rm.role_id, g.gid, '1', NOW(), '1', NOW(), b'0', rm.tenant_id
FROM system_role_menu rm
JOIN (
              SELECT 34010 AS gid, cid FROM (SELECT 34301 AS cid UNION ALL SELECT 34302 UNION ALL SELECT 34370
                                      UNION ALL SELECT 34380 UNION ALL SELECT 34350) t
    UNION ALL SELECT 34011, cid FROM (SELECT 34390 AS cid UNION ALL SELECT 34396) t
    UNION ALL SELECT 34012, cid FROM (SELECT 34410 AS cid UNION ALL SELECT 34420 UNION ALL SELECT 34430
                                      UNION ALL SELECT 34440) t
    UNION ALL SELECT 34013, cid FROM (SELECT 34600 AS cid UNION ALL SELECT 34610 UNION ALL SELECT 34620
                                      UNION ALL SELECT 34630 UNION ALL SELECT 34640 UNION ALL SELECT 34650
                                      UNION ALL SELECT 34660 UNION ALL SELECT 34670 UNION ALL SELECT 34680
                                      UNION ALL SELECT 34690) t
    UNION ALL SELECT 34014, cid FROM (SELECT 34463 AS cid UNION ALL SELECT 34400 UNION ALL SELECT 34700) t
    UNION ALL SELECT 34015, cid FROM (SELECT 34500 AS cid UNION ALL SELECT 34510 UNION ALL SELECT 34520
                                      UNION ALL SELECT 34530 UNION ALL SELECT 34540) t
) g ON g.cid = rm.menu_id
WHERE rm.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x
                  WHERE x.role_id = rm.role_id AND x.menu_id = g.gid AND x.deleted = b'0');

-- ============ 回滚 ============
-- UPDATE system_menu SET parent_id = 34000 WHERE parent_id BETWEEN 34010 AND 34015;
-- DELETE FROM system_role_menu WHERE menu_id BETWEEN 34010 AND 34015;
-- DELETE FROM system_menu      WHERE id     BETWEEN 34010 AND 34015;
-- （叶子原来的 sort 值见合并前的 190..220 序列，回滚后如需还原顺序再逐个 UPDATE。）
