-- 秦川化工产业园发展有限公司（租户 2020）招聘岗位种子数据。
-- 仅写入 HRM 招聘职位类型、招聘渠道和招聘职位；脚本可重复执行。
SET NAMES utf8mb4;

CREATE TEMPORARY TABLE tmp_qinchuan_post_type (
  id bigint NOT NULL,
  name varchar(255) NOT NULL,
  parent_id bigint NOT NULL,
  sort int NOT NULL,
  PRIMARY KEY (id)
) ENGINE=MEMORY;

INSERT INTO tmp_qinchuan_post_type (id, name, parent_id, sort) VALUES
(2020001, '一线施工技工', 0, 1),
(2020002, '防腐工', 2020001, 1),
(2020003, '保温工', 2020001, 2),
(2020004, '喷砂工', 2020001, 3),
(2020005, '油漆工', 2020001, 4),
(2020006, '管工', 2020001, 5),
(2020007, '铆工', 2020001, 6),
(2020008, '焊工', 2020001, 7),
(2020009, '普工/力工', 2020001, 8),
(2020010, '项目技术与管理人员', 0, 2),
(2020011, '项目经理', 2020010, 1),
(2020012, '技术负责人/工程师', 2020010, 2),
(2020013, '施工员', 2020010, 3),
(2020014, '预算员/造价员', 2020010, 4),
(2020015, '安全员', 2020010, 5),
(2020016, '后勤与质量保障岗', 0, 3),
(2020017, '质检员', 2020016, 1),
(2020018, '资料员', 2020016, 2),
(2020019, '材料员/库管', 2020016, 3),
(2020020, '设备维修工', 2020016, 4),
(2020021, '市场与行政岗', 0, 4),
(2020022, '市场业务经理', 2020021, 1),
(2020023, '投标专员', 2020021, 2),
(2020024, '采购员', 2020021, 3),
(2020025, '财务会计', 2020021, 4),
(2020026, '行政人事专员', 2020021, 5);

INSERT INTO hrm_recruit_post_type
  (id, name, parent_id, sort, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT id, name, parent_id, sort, 0, 'seed', NOW(), 'seed', NOW(), b'0', 2020
FROM tmp_qinchuan_post_type
ON DUPLICATE KEY UPDATE
  name = VALUES(name), parent_id = VALUES(parent_id), sort = VALUES(sort),
  status = 0, updater = 'seed', update_time = NOW(), deleted = b'0', tenant_id = 2020;

CREATE TEMPORARY TABLE tmp_qinchuan_channel (
  seq int NOT NULL,
  id bigint NOT NULL,
  name varchar(255) NOT NULL,
  PRIMARY KEY (seq), UNIQUE KEY (id)
) ENGINE=MEMORY;

INSERT INTO tmp_qinchuan_channel (seq, id, name) VALUES
(1, 2020101, 'BOSS直聘'),
(2, 2020102, '智联招聘'),
(3, 2020103, '前程无忧'),
(4, 2020104, '猎聘网'),
(5, 2020105, '员工推荐'),
(6, 2020106, '微信招聘'),
(7, 2020107, '线下招聘会'),
(8, 2020108, '内部举荐');

INSERT INTO hrm_recruit_channel
  (id, system_flag, status, name, sort, remark, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT id, b'0', 0, name, seq, '秦川化工产业园招聘渠道', 'seed', NOW(), 'seed', NOW(), b'0', 2020
FROM tmp_qinchuan_channel
ON DUPLICATE KEY UPDATE
  status = 0, name = VALUES(name), sort = VALUES(sort), remark = VALUES(remark),
  updater = 'seed', update_time = NOW(), deleted = b'0', tenant_id = 2020;

CREATE TEMPORARY TABLE tmp_qinchuan_location (
  seq int NOT NULL,
  area_id int NOT NULL,
  area_name varchar(255) NOT NULL,
  PRIMARY KEY (seq)
) ENGINE=MEMORY;

INSERT INTO tmp_qinchuan_location (seq, area_id, area_name) VALUES
(1, 610802, '陕西省榆林市榆阳区'),
(2, 610881, '陕西省榆林市神木市'),
(3, 610822, '陕西省榆林市府谷县'),
(4, 610824, '陕西省榆林市靖边县'),
(5, 610825, '陕西省榆林市定边县'),
(6, 610803, '陕西省榆林市横山区'),
(7, 610827, '陕西省榆林市米脂县'),
(8, 610826, '陕西省榆林市绥德县'),
(9, 610829, '陕西省榆林市吴堡县'),
(10, 610830, '陕西省榆林市清涧县'),
(11, 610831, '陕西省榆林市子洲县'),
(12, 610602, '陕西省延安市宝塔区'),
(13, 610625, '陕西省延安市志丹县'),
(14, 610603, '陕西省延安市安塞区'),
(15, 610628, '陕西省延安市富县'),
(16, 610629, '陕西省延安市洛川县'),
(17, 610112, '陕西省西安市未央区'),
(18, 610402, '陕西省咸阳市秦都区'),
(19, 610302, '陕西省宝鸡市渭滨区'),
(20, 610526, '陕西省渭南市蒲城县'),
(21, 610202, '陕西省铜川市王益区'),
(22, 620105, '甘肃省兰州市安宁区'),
(23, 640104, '宁夏回族自治区银川市兴庆区'),
(24, 650106, '新疆维吾尔自治区乌鲁木齐市头屯河区');

CREATE TEMPORARY TABLE tmp_qinchuan_role (
  seq int NOT NULL,
  post_type_id bigint NOT NULL,
  post_name varchar(255) NOT NULL,
  dept_id bigint NOT NULL,
  recruit_num int NOT NULL,
  work_time int NOT NULL,
  education_require int NOT NULL,
  min_salary decimal(10,2) NOT NULL,
  max_salary decimal(10,2) NOT NULL,
  PRIMARY KEY (seq)
) ENGINE=MEMORY;

INSERT INTO tmp_qinchuan_role
  (seq, post_type_id, post_name, dept_id, recruit_num, work_time, education_require, min_salary, max_salary)
VALUES
(1, 2020002, '防腐工', 50134, 8, 2, 1, 7000, 10000),
(2, 2020003, '保温工', 50134, 8, 2, 1, 6800, 9800),
(3, 2020004, '喷砂工', 50134, 6, 2, 1, 7200, 10500),
(4, 2020005, '油漆工', 50134, 6, 2, 1, 6800, 9800),
(5, 2020006, '管工', 50134, 6, 3, 1, 7500, 11000),
(6, 2020007, '铆工', 50134, 6, 3, 1, 7500, 11000),
(7, 2020008, '焊工', 50134, 8, 3, 1, 8000, 12000),
(8, 2020009, '普工/力工', 50134, 10, 0, 1, 5500, 8000),
(9, 2020011, '项目经理', 50134, 2, 5, 3, 14000, 22000),
(10, 2020012, '技术负责人/工程师', 50134, 3, 5, 3, 12000, 19000),
(11, 2020013, '施工员', 50134, 4, 3, 2, 8000, 13000),
(12, 2020014, '预算员/造价员', 50136, 3, 3, 3, 8500, 14000),
(13, 2020015, '安全员', 50137, 4, 3, 2, 7500, 12000),
(14, 2020017, '质检员', 50137, 4, 3, 2, 7500, 12000),
(15, 2020018, '资料员', 50134, 4, 2, 2, 6500, 10000),
(16, 2020019, '材料员/库管', 50135, 4, 2, 1, 6200, 9500),
(17, 2020020, '设备维修工', 50134, 3, 3, 1, 7500, 11500),
(18, 2020022, '市场业务经理', 50135, 3, 3, 3, 10000, 18000),
(19, 2020023, '投标专员', 50135, 3, 2, 3, 8000, 13000),
(20, 2020024, '采购员', 50135, 3, 2, 2, 7500, 12000),
(21, 2020025, '财务会计', 50138, 2, 3, 3, 8000, 14000),
(22, 2020026, '行政人事专员', 50139, 2, 2, 2, 6500, 10500);

INSERT INTO hrm_recruit_post
  (id, post_name, dept_id, job_nature, area_id, recruit_num, reason, work_time, education_require,
   min_salary, max_salary, salary_unit, min_age, max_age, latest_entry_time, owner_employee_id,
   interview_employee_ids, description, emergency_level, post_type_id, channel_id, status, stop_reason,
   creator, create_time, updater, update_time, deleted, tenant_id)
WITH RECURSIVE nums (seq) AS (
  SELECT 1
  UNION ALL
  SELECT seq + 1 FROM nums WHERE seq < 200
)
SELECT 2021000 + nums.seq,
       role.post_name,
       role.dept_id,
       1,
       location.area_id,
       role.recruit_num,
       '秦川化工产业园项目建设与区域业务扩展',
       role.work_time,
       role.education_require,
       role.min_salary,
       role.max_salary,
       1,
       20,
       55,
       '2026-12-31 18:00:00',
       NULL,
       NULL,
       CONCAT('负责', role.post_name, '相关防腐保温工程工作，服务陕西及西北区域项目。'),
       2,
       role.post_type_id,
       channel.id,
       1,
       NULL,
       'seed',
       NOW(),
       'seed',
       NOW(),
       b'0',
       2020
FROM nums
JOIN tmp_qinchuan_role role ON role.seq = MOD(nums.seq - 1, 22) + 1
JOIN tmp_qinchuan_location location ON location.seq = MOD(nums.seq - 1, 24) + 1
JOIN tmp_qinchuan_channel channel ON channel.seq = MOD(nums.seq - 1, 8) + 1
ON DUPLICATE KEY UPDATE
  post_name = VALUES(post_name), dept_id = VALUES(dept_id), job_nature = VALUES(job_nature),
  area_id = VALUES(area_id), recruit_num = VALUES(recruit_num), reason = VALUES(reason),
  work_time = VALUES(work_time), education_require = VALUES(education_require),
  min_salary = VALUES(min_salary), max_salary = VALUES(max_salary), salary_unit = VALUES(salary_unit),
  min_age = VALUES(min_age), max_age = VALUES(max_age), latest_entry_time = VALUES(latest_entry_time),
  description = VALUES(description), emergency_level = VALUES(emergency_level),
  post_type_id = VALUES(post_type_id), channel_id = VALUES(channel_id), status = 1,
  stop_reason = NULL, updater = 'seed', update_time = NOW(), deleted = b'0', tenant_id = 2020;

DROP TEMPORARY TABLE tmp_qinchuan_role;
DROP TEMPORARY TABLE tmp_qinchuan_location;
DROP TEMPORARY TABLE tmp_qinchuan_channel;
DROP TEMPORARY TABLE tmp_qinchuan_post_type;
