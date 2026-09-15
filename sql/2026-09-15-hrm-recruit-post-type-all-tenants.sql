-- HRM 全租户招聘职位分类标准目录。
-- 为每个有效企业租户补齐统一的一级/二级职位分类；已存在的分类保持不变，脚本可重复执行。
SET NAMES utf8mb4;

CREATE TEMPORARY TABLE tmp_hrm_recruit_category_root (
  name varchar(255) NOT NULL,
  sort int NOT NULL,
  PRIMARY KEY (name)
) ENGINE=MEMORY;

INSERT INTO tmp_hrm_recruit_category_root (name, sort) VALUES
('一线施工技工', 1),
('项目技术与管理人员', 2),
('后勤与质量保障岗', 3),
('市场与行政岗', 4),
('其他', 5);

INSERT INTO hrm_recruit_post_type
  (name, parent_id, sort, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT category.name, 0, category.sort, 0, 'seed', NOW(), 'seed', NOW(), b'0', tenant.id
FROM system_tenant tenant
JOIN tmp_hrm_recruit_category_root category
WHERE tenant.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM hrm_recruit_post_type existing
    WHERE existing.tenant_id = tenant.id
      AND existing.parent_id = 0
      AND existing.name = category.name
      AND existing.deleted = b'0'
  );

CREATE TEMPORARY TABLE tmp_hrm_recruit_category_leaf (
  root_name varchar(255) NOT NULL,
  name varchar(255) NOT NULL,
  sort int NOT NULL,
  PRIMARY KEY (root_name, name)
) ENGINE=MEMORY;

INSERT INTO tmp_hrm_recruit_category_leaf (root_name, name, sort) VALUES
('一线施工技工', '防腐工', 1),
('一线施工技工', '保温工', 2),
('一线施工技工', '喷砂工', 3),
('一线施工技工', '油漆工', 4),
('一线施工技工', '管工', 5),
('一线施工技工', '铆工', 6),
('一线施工技工', '焊工', 7),
('一线施工技工', '架子工', 8),
('一线施工技工', '电工', 9),
('一线施工技工', '瓦工', 10),
('一线施工技工', '钢筋工', 11),
('一线施工技工', '木工', 12),
('一线施工技工', '缝纫工', 13),
('一线施工技工', '普工/力工', 14),
('项目技术与管理人员', '项目经理', 1),
('项目技术与管理人员', '项目副经理', 2),
('项目技术与管理人员', '技术负责人/工程师', 3),
('项目技术与管理人员', '施工员', 4),
('项目技术与管理人员', '施工队长/工长', 5),
('项目技术与管理人员', '预算员/造价员', 6),
('项目技术与管理人员', '安全员', 7),
('后勤与质量保障岗', '质检员', 1),
('后勤与质量保障岗', '试验/检测员', 2),
('后勤与质量保障岗', '资料员', 3),
('后勤与质量保障岗', '材料员/库管', 4),
('后勤与质量保障岗', '设备维修工', 5),
('后勤与质量保障岗', '机械设备管理员', 6),
('市场与行政岗', '市场业务经理', 1),
('市场与行政岗', '投标专员', 2),
('市场与行政岗', '采购员', 3),
('市场与行政岗', '财务会计', 4),
('市场与行政岗', '行政人事专员', 5),
('其他', '防腐保温管理（校招管培方向）', 1);

INSERT INTO hrm_recruit_post_type
  (name, parent_id, sort, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT leaf.name, root.id, leaf.sort, 0, 'seed', NOW(), 'seed', NOW(), b'0', tenant.id
FROM system_tenant tenant
JOIN tmp_hrm_recruit_category_root root_category
  ON 1 = 1
JOIN hrm_recruit_post_type root
  ON root.tenant_id = tenant.id
 AND root.parent_id = 0
 AND root.name = root_category.name
 AND root.deleted = b'0'
JOIN tmp_hrm_recruit_category_leaf leaf
  ON leaf.root_name = root_category.name
WHERE tenant.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM hrm_recruit_post_type existing
    WHERE existing.tenant_id = tenant.id
      AND existing.parent_id = root.id
      AND existing.name = leaf.name
      AND existing.deleted = b'0'
  );

DROP TEMPORARY TABLE tmp_hrm_recruit_category_leaf;
DROP TEMPORARY TABLE tmp_hrm_recruit_category_root;
