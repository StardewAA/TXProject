-- =====================================================================
-- 云培训学院 - 基础数据字典（岗位工种 / 工种资格证 / 学习地图）
--
-- 数据来源：
--   《云培训学院-岗位工种表.xlsx》      56 条
--   《云培训学院-各种资格证.xlsx》      46 条
--   《云培训学院-学习地图示例.xlsx》    213 条（示例覆盖 7 个工种）
--   《赵升茹-数据字典与一级子模块关联.docx》（关联关系定义）
--
-- 关联关系（依据《数据字典与一级子模块关联》）：
--   1) training_job.learn_domain_codes -> training_course_category.code（学习域 L1 域 / L2 组）
--   2) training_job.max_level          -> training_growth_level.code（最高成长等级）
--   3) training_job_cert.job_code      -> training_job.code
--   4) training_learning_map.job_code  -> training_job.code
--   5) training_learning_map.course_code -> training_course_category.code（L3 课程级）
--   6) training_learning_map.stage     -> 晋级门槛阶段（entry/core/advanced/technician）
--
-- 执行：mysql -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < training_base_dict_seed.sql
-- 说明：脚本可重复执行（建表 IF NOT EXISTS；数据按唯一键幂等更新）
-- =====================================================================

-- 1. 岗位工种字典表
CREATE TABLE IF NOT EXISTS `training_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位工种ID',
  `code` varchar(20) NOT NULL COMMENT '工种编码：OP-* 操作序列 / TC-* 技术序列 / QC-* 质检检测序列 / MG-* 管理序列 / SP-* 支持序列',
  `name` varchar(100) NOT NULL COMMENT '工种名称',
  `seq_code` varchar(10) NOT NULL COMMENT '序列编码：OP操作 TC技术 QC质检检测 MG管理 SP支持',
  `seq_name` varchar(50) NOT NULL COMMENT '序列名称',
  `biz_category` varchar(200) DEFAULT NULL COMMENT '业务类目（对应文档章节号，如 一/二/三/二十三）',
  `learn_domain` varchar(200) DEFAULT NULL COMMENT '学习域/课程组（原始文本，如 A,B01-B03 / 全域）',
  `learn_domain_codes` varchar(500) DEFAULT NULL COMMENT '规范化学习域/课程组编码（逗号分隔，关联 training_course_category.code；* 表示全域）',
  `key_skills` varchar(500) DEFAULT NULL COMMENT '关键技能',
  `max_level` varchar(30) DEFAULT NULL COMMENT '最高成长等级（关联 training_growth_level.code：TECHNICIAN技师 / SENIOR_TECHNICIAN高级技师）',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` varchar(20) NOT NULL DEFAULT 'active' COMMENT '状态：active 启用 / disabled 停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_seq` (`seq_code`),
  KEY `idx_max_level` (`max_level`),
  KEY `idx_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位工种字典表（云培训学院基础数据字典）';

-- 2. 工种资格证要求表
CREATE TABLE IF NOT EXISTS `training_job_cert` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '资格证ID',
  `job_code` varchar(20) NOT NULL COMMENT '工种编码（关联 training_job.code）',
  `job_name` varchar(100) DEFAULT NULL COMMENT '工种名称（冗余，便于展示）',
  `cert_name` varchar(100) NOT NULL COMMENT '资格证名称',
  `is_required` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否必须持证上岗：0否 1是',
  `valid_months` int DEFAULT NULL COMMENT '有效期（月），NULL 表示长期有效',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_cert` (`job_code`, `cert_name`),
  KEY `idx_job_code` (`job_code`),
  KEY `idx_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工种资格证要求表（持证上岗校验依据）';

-- 3. 工种学习地图表
CREATE TABLE IF NOT EXISTS `training_learning_map` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '学习地图ID',
  `job_code` varchar(20) NOT NULL COMMENT '工种编码（关联 training_job.code）',
  `job_name` varchar(100) DEFAULT NULL COMMENT '工种名称（冗余，便于展示）',
  `stage` varchar(20) NOT NULL COMMENT '成长阶段：entry入门段(L1→L2) / core核心段(L2→L3) / advanced进阶段(L3→L4) / technician技师段(L4→L5)',
  `stage_name` varchar(50) DEFAULT NULL COMMENT '阶段名称（入门段(L1→L2) 等）',
  `stage_no` int NOT NULL DEFAULT 1 COMMENT '阶段序号：1入门 2核心 3进阶 4技师',
  `course_code` varchar(20) NOT NULL COMMENT '课程编码（关联 training_course_category.code，level=3 课程级）',
  `course_name` varchar(200) DEFAULT NULL COMMENT '课程名称（冗余，便于展示）',
  `is_required` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否必修：0否 1是',
  `sort` int NOT NULL DEFAULT 0 COMMENT '阶段内排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_stage_course` (`job_code`, `stage`, `course_code`),
  KEY `idx_job_code` (`job_code`),
  KEY `idx_stage` (`stage`),
  KEY `idx_course_code` (`course_code`),
  KEY `idx_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工种学习地图表（阶段化课程路径）';

-- 4. 导入岗位工种 56 条（按 code 幂等）
INSERT INTO `training_job`
  (`code`, `name`, `seq_code`, `seq_name`, `biz_category`, `learn_domain`, `learn_domain_codes`,
   `key_skills`, `max_level`, `sort`, `status`, `creator`, `tenant_id`) VALUES
('OP-01', '表面处理工（喷砂/抛丸）', 'OP', '操作序列', '一/二/三/二十三', 'A,B01', 'A,B01', '除锈等级判定、喷砂设备操作', 'SENIOR_TECHNICIAN', 1, 'active', 'admin', 1),
('OP-02', '防腐涂装工', 'OP', '操作序列', '一/二/二十三~三十四', 'A,B01-B03', 'A,B01,B02,B03', '高压无气喷涂、膜厚控制', 'SENIOR_TECHNICIAN', 2, 'active', 'admin', 1),
('OP-03', '储罐防腐工', 'OP', '操作序列', '一(1-6)/二十一-4', 'A,B04', 'A,B04', '罐内防腐、内衬玻璃钢', 'SENIOR_TECHNICIAN', 3, 'active', 'admin', 1),
('OP-04', '3PE/粉末防腐工', 'OP', '操作序列', '三/二十二', 'A,B05', 'A,B05', '三层PE挤出、FBE熔结', 'SENIOR_TECHNICIAN', 4, 'active', 'admin', 1),
('OP-05', '补口补伤工', 'OP', '操作序列', '八-2/三十四-5', 'A,B06', 'A,B06', '热收缩套施工、搭接控制', 'SENIOR_TECHNICIAN', 5, 'active', 'admin', 1),
('OP-06', '内涂施工工', 'OP', '操作序列', '十五/三十四-2', 'A,B07', 'A,B07', '内涂设备操作、内涂层检测', 'SENIOR_TECHNICIAN', 6, 'active', 'admin', 1),
('OP-07', '特种防腐工（玻璃钢/陶瓷）', 'OP', '操作序列', '二十六/二十七/二十五', 'A,B08', 'A,B08', '环氧玻璃钢、金属陶瓷内衬', 'SENIOR_TECHNICIAN', 7, 'active', 'admin', 1),
('OP-08', '保温工', 'OP', '操作序列', '二/十六/三十三', 'A,C01-C02', 'A,C01,C02', '管道/设备保温、异形件', 'SENIOR_TECHNICIAN', 8, 'active', 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'OP', '操作序列', '四', 'A,C03', 'A,C03', '聚氨酯/泡沫玻璃、防潮层', 'SENIOR_TECHNICIAN', 9, 'active', 'admin', 1),
('OP-10', '铁皮加工安装工', 'OP', '操作序列', '二/四/三十三', 'A,C0203', 'A,C02', '保护层放样、咬口制作', 'SENIOR_TECHNICIAN', 10, 'active', 'admin', 1),
('OP-11', '冬防保温工', 'OP', '操作序列', '十六', 'A,C04', 'A,C04', '冬防方案实施、伴热配合', 'SENIOR_TECHNICIAN', 11, 'active', 'admin', 1),
('OP-12', '内衬施工工', 'OP', '操作序列', '二十一(1/2/4/5)/三十五', 'A,D01', 'A,D01', '高分子PE内衬、非金属油管内衬', 'SENIOR_TECHNICIAN', 12, 'active', 'admin', 1),
('OP-13', '油管修复工', 'OP', '操作序列', '五/二十八/二十五', 'A,D02', 'A,D02', '油管清洗、检验、修复', 'SENIOR_TECHNICIAN', 13, 'active', 'admin', 1),
('OP-14', '油杆修复工', 'OP', '操作序列', '六', 'A,D02', 'A,D02', '油杆校直、修复', 'SENIOR_TECHNICIAN', 14, 'active', 'admin', 1),
('OP-15', '摩擦焊操作工', 'OP', '操作序列', '十八', 'A,D0203', 'A,D02', '摩擦焊接头接箍修复', 'SENIOR_TECHNICIAN', 15, 'active', 'admin', 1),
('OP-16', '管道修复补强工', 'OP', '操作序列', '八-1/八-3/二十', 'A,D03,D04', 'A,D03,D04', '补强套筒、带压堵漏', 'SENIOR_TECHNICIAN', 16, 'active', 'admin', 1),
('OP-17', '套管修复工', 'OP', '操作序列', '十三/二十', 'A,D04', 'A,D04', '套管/套筒修复', 'SENIOR_TECHNICIAN', 17, 'active', 'admin', 1),
('OP-18', '装备钻头修复工', 'OP', '操作序列', '七', 'A,D0403', 'A,D04', '钻头修复、装备维修', 'SENIOR_TECHNICIAN', 18, 'active', 'admin', 1),
('OP-19', '井口作业防腐工', 'OP', '操作序列', '三十/十七', 'A,B0804', 'A,B08', '井口修复与防腐', 'SENIOR_TECHNICIAN', 19, 'active', 'admin', 1),
('OP-20', '防蜡防垢作业工', 'OP', '操作序列', '十一', 'A,B0805', 'A,B08', '清蜡除垢工艺', 'SENIOR_TECHNICIAN', 20, 'active', 'admin', 1),
('OP-21', '管线安装工', 'OP', '操作序列', '十二/十九', 'A,G03', 'A,G03', '产能建设管线敷设', 'SENIOR_TECHNICIAN', 21, 'active', 'admin', 1),
('OP-22', '起重吊装指挥/司索', 'OP', '操作序列', '通用', 'A,A0302', 'A,A03', '吊装指挥、绑扎', 'SENIOR_TECHNICIAN', 22, 'active', 'admin', 1),
('OP-23', '电焊工', 'OP', '操作序列', '八/十二/十九/二十', 'A', 'A', '手工电弧焊、氩弧焊', 'SENIOR_TECHNICIAN', 23, 'active', 'admin', 1),
('OP-24', '电工（维修电工）', 'OP', '操作序列', '五十九/通用', 'A,A0304', 'A,A03', '临时用电、电伴热', 'SENIOR_TECHNICIAN', 24, 'active', 'admin', 1),
('OP-25', '架子工', 'OP', '操作序列', '通用', 'A,A0302', 'A,A03', '脚手架搭拆', 'SENIOR_TECHNICIAN', 25, 'active', 'admin', 1),
('TC-01', '防腐工艺技术员', 'TC', '技术序列', '一/二/三/二十二~三十四', 'B,G01', 'B,G01', '防腐方案编制、涂层体系选型', 'TECHNICIAN', 26, 'active', 'admin', 1),
('TC-02', '保温绝热技术员', 'TC', '技术序列', '二/四/十六/三十三', 'C,G01', 'C,G01', '保温厚度计算、保冷方案', 'TECHNICIAN', 27, 'active', 'admin', 1),
('TC-03', '内衬修复技术员', 'TC', '技术序列', '二十一/五/六/八', 'D,G01', 'D,G01', '内衬选型、缺陷评估修复选型', 'TECHNICIAN', 28, 'active', 'admin', 1),
('TC-04', '材料工程师', 'TC', '技术序列', '九/十四/三十五', 'F', 'F', '材料指标验收、供方技术评审', 'TECHNICIAN', 29, 'active', 'admin', 1),
('TC-05', '焊接工艺技术员', 'TC', '技术序列', '八/十八/十二', 'D,G01', 'D,G01', 'WPS编制、焊接工艺评定', 'TECHNICIAN', 30, 'active', 'admin', 1),
('TC-06', '标准与资料员', 'TC', '技术序列', '十', 'A0502,G', 'A05,G', '施工资料、验收标准、留证', 'TECHNICIAN', 31, 'active', 'admin', 1),
('QC-01', '防腐涂层检验员', 'QC', '质检检测序列', '一/二/三/十五', 'B0303,B0304', 'B03', '膜厚、漏点、附着力检测', 'TECHNICIAN', 32, 'active', 'admin', 1),
('QC-02', '保温工程检验员', 'QC', '质检检测序列', '二/四/十六', 'C', 'C', '厚度、保护层、防潮层验收', 'TECHNICIAN', 33, 'active', 'admin', 1),
('QC-03', '超声检测员（UT）', 'QC', '质检检测序列', '三十九~六十三', 'E0102', 'E01', 'UT探伤、测厚', 'TECHNICIAN', 34, 'active', 'admin', 1),
('QC-04', '射线检测员（RT）', 'QC', '质检检测序列', '四十三/四十九', 'E0105', 'E01', 'RT拍片、评片、辐射安全', 'TECHNICIAN', 35, 'active', 'admin', 1),
('QC-05', '磁粉/渗透检测员（MT/PT）', 'QC', '质检检测序列', '四十~四十三', 'E0103,E0104', 'E01', 'MT/PT表面缺陷检测', 'TECHNICIAN', 36, 'active', 'admin', 1),
('QC-06', '涡流/漏磁检测员', 'QC', '质检检测序列', '三十九/四十八', 'E0106', 'E01', '管材涡流、罐底漏磁', 'TECHNICIAN', 37, 'active', 'admin', 1),
('QC-07', '管材管件检测员', 'QC', '质检检测序列', '三十九/四十/五十一/五十二/六十二/六十三', 'E02', 'E02', '废油管分级、钻具油套管检测', 'TECHNICIAN', 38, 'active', 'admin', 1),
('QC-08', '压力容器/管道检验员', 'QC', '质检检测序列', '四十二/四十九/五十五/五十六/六十', 'E03,E0204', 'E03,E02', '容器管道定期检验', 'TECHNICIAN', 39, 'active', 'admin', 1),
('QC-09', '储罐检验员', 'QC', '质检检测序列', '四十八', 'E0302', 'E03', '罐壁罐底检测', 'TECHNICIAN', 40, 'active', 'admin', 1),
('QC-10', '电气防雷防爆检测员', 'QC', '质检检测序列', '四十七/五十九', 'E0403,E0404', 'E04', '防雷接地、防爆检测', 'TECHNICIAN', 41, 'active', 'admin', 1),
('QC-11', '气密性与气体检测员', 'QC', '质检检测序列', '五十三/五十七', 'E0401,E0402', 'E04', '气密试验、气体分析', 'TECHNICIAN', 42, 'active', 'admin', 1),
('QC-12', '理化试验员', 'QC', '质检检测序列', '四十一', 'E0501', 'E05', '力学性能、金相分析', 'TECHNICIAN', 43, 'active', 'admin', 1),
('QC-13', '计量检定员', 'QC', '质检检测序列', '五十四', 'E0502', 'E05', '计量器具检定校准', 'TECHNICIAN', 44, 'active', 'admin', 1),
('QC-14', '设施设备检测员', 'QC', '质检检测序列', '四十四/五十/六十一', 'E0305,E0306', 'E03', '设施、机井架检测', 'TECHNICIAN', 45, 'active', 'admin', 1),
('MG-01', '班组长', 'MG', '管理序列', '通用', 'A,G03,H', 'A,G03,H', '派工、日常管理、报工审核', 'TECHNICIAN', 46, 'active', 'admin', 1),
('MG-02', '工段长/施工负责人', 'MG', '管理序列', '通用', 'A,G01-G03', 'A,G01,G02,G03', '技术交底、进度质量安全', 'TECHNICIAN', 47, 'active', 'admin', 1),
('MG-03', '项目经理', 'MG', '管理序列', '通用', 'G,H,F02', 'G,H,F02', '项目策划、成本、结算', 'TECHNICIAN', 48, 'active', 'admin', 1),
('MG-04', '生产调度', 'MG', '管理序列', '通用', 'G03,H', 'G03,H', '资源调度、计划平衡', 'TECHNICIAN', 49, 'active', 'admin', 1),
('MG-05', '培训师/内部讲师', 'MG', '管理序列', '通用', '全域', '*', '授课、课程编写、实操考评', 'TECHNICIAN', 50, 'active', 'admin', 1),
('SP-01', 'HSE安全员', 'SP', '支持序列', '通用', 'A全域', 'A', '风险识别、作业许可、应急', 'TECHNICIAN', 51, 'active', 'admin', 1),
('SP-02', '设备管理员', 'SP', '支持序列', '通用', 'A0304,B0102', 'A03,B01', '喷砂机/喷涂机等设备管理', 'TECHNICIAN', 52, 'active', 'admin', 1),
('SP-03', '材料采购员', 'SP', '支持序列', '九/十四/三十五', 'F02', 'F02', '采购流程、供方管理', 'TECHNICIAN', 53, 'active', 'admin', 1),
('SP-04', '仓储管理员', 'SP', '支持序列', '九/三十五', 'F0202', 'F02', '材料收发、保质期管理', 'TECHNICIAN', 54, 'active', 'admin', 1),
('SP-05', '数字化/MES专员', 'SP', '支持序列', '十/十二', 'H全域', 'H', '系统运维、数据治理', 'TECHNICIAN', 55, 'active', 'admin', 1),
('SP-06', '商务与投标专员', 'SP', '支持序列', '十', 'H0202,G0302', 'H02,G03', '招投标响应、工程量结算', 'TECHNICIAN', 56, 'active', 'admin', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `seq_code` = VALUES(`seq_code`), `seq_name` = VALUES(`seq_name`),
  `biz_category` = VALUES(`biz_category`), `learn_domain` = VALUES(`learn_domain`),
  `learn_domain_codes` = VALUES(`learn_domain_codes`), `key_skills` = VALUES(`key_skills`),
  `max_level` = VALUES(`max_level`), `sort` = VALUES(`sort`), `updater` = 'admin';

-- 5. 导入工种资格证 46 条（按 工种+证名 幂等）
INSERT INTO `training_job_cert`
  (`job_code`, `job_name`, `cert_name`, `is_required`, `valid_months`, `sort`, `creator`, `tenant_id`) VALUES
('OP-01', '表面处理工（喷砂/抛丸）', '受限空间作业证', b'1', '36', 1, 'admin', 1),
('OP-01', '表面处理工（喷砂/抛丸）', '高处作业证', b'1', '24', 2, 'admin', 1),
('OP-02', '防腐涂装工', '动火作业许可培训', b'1', '12', 3, 'admin', 1),
('OP-02', '防腐涂装工', '受限空间作业证', b'1', '36', 4, 'admin', 1),
('OP-02', '防腐涂装工', '高处作业证', b'1', '24', 5, 'admin', 1),
('OP-03', '储罐防腐工', '受限空间作业证', b'1', '36', 6, 'admin', 1),
('OP-03', '储罐防腐工', '高处作业证', b'1', '24', 7, 'admin', 1),
('OP-04', '3PE/粉末防腐工', '动火作业许可培训', b'1', '12', 8, 'admin', 1),
('OP-05', '补口补伤工', '动火作业许可培训', b'1', '12', 9, 'admin', 1),
('OP-06', '内涂施工工', '受限空间作业证', b'1', '36', 10, 'admin', 1),
('OP-07', '特种防腐工（玻璃钢/陶瓷）', '受限空间作业证', b'1', '36', 11, 'admin', 1),
('OP-08', '保温工', '高处作业证', b'1', '24', 12, 'admin', 1),
('OP-09', '保冷工（绝热施工）', '高处作业证', b'1', '24', 13, 'admin', 1),
('OP-10', '铁皮加工安装工', '高处作业证', b'1', '24', 14, 'admin', 1),
('OP-11', '冬防保温工', '高处作业证', b'1', '24', 15, 'admin', 1),
('OP-12', '内衬施工工', '受限空间作业证', b'1', '36', 16, 'admin', 1),
('OP-15', '摩擦焊操作工', '焊工作业证', b'1', '36', 17, 'admin', 1),
('OP-15', '摩擦焊操作工', '动火作业许可培训', b'1', '12', 18, 'admin', 1),
('OP-16', '管道修复补强工', '动火作业许可培训', b'1', '12', 19, 'admin', 1),
('OP-16', '管道修复补强工', '受限空间作业证', b'1', '36', 20, 'admin', 1),
('OP-18', '装备钻头修复工', '动火作业许可培训', b'1', '12', 21, 'admin', 1),
('OP-19', '井口作业防腐工', '高处作业证', b'1', '24', 22, 'admin', 1),
('OP-19', '井口作业防腐工', '动火作业许可培训', b'1', '12', 23, 'admin', 1),
('OP-21', '管线安装工', '动火作业许可培训', b'1', '12', 24, 'admin', 1),
('OP-21', '管线安装工', '高处作业证', b'1', '24', 25, 'admin', 1),
('OP-22', '起重吊装指挥/司索', '起重信号司索作业证', b'1', '36', 26, 'admin', 1),
('OP-23', '电焊工', '焊工作业证', b'1', '36', 27, 'admin', 1),
('OP-23', '电焊工', '动火作业许可培训', b'1', '12', 28, 'admin', 1),
('OP-24', '电工（维修电工）', '低压/高压电工作业证', b'1', '36', 29, 'admin', 1),
('OP-25', '架子工', '登高架设作业证', b'1', '36', 30, 'admin', 1),
('TC-05', '焊接工艺技术员', '焊接工艺评定培训', b'0', NULL, 31, 'admin', 1),
('QC-01', '防腐涂层检验员', '防腐涂层检测人员证', b'1', '48', 32, 'admin', 1),
('QC-03', '超声检测员（UT）', '无损检测UT Ⅱ级', b'1', '60', 33, 'admin', 1),
('QC-04', '射线检测员（RT）', '无损检测RT Ⅱ级', b'1', '60', 34, 'admin', 1),
('QC-04', '射线检测员（RT）', '放射工作人员证', b'1', '24', 35, 'admin', 1),
('QC-05', '磁粉/渗透检测员（MT/PT）', '无损检测MT Ⅱ级', b'1', '60', 36, 'admin', 1),
('QC-05', '磁粉/渗透检测员（MT/PT）', '无损检测PT Ⅱ级', b'1', '60', 37, 'admin', 1),
('QC-06', '涡流/漏磁检测员', '无损检测ET Ⅱ级', b'1', '60', 38, 'admin', 1),
('QC-07', '管材管件检测员', '无损检测UT Ⅱ级', b'1', '60', 39, 'admin', 1),
('QC-07', '管材管件检测员', '无损检测MT Ⅱ级', b'1', '60', 40, 'admin', 1),
('QC-08', '压力容器/管道检验员', '特种设备检验员证', b'1', '48', 41, 'admin', 1),
('QC-09', '储罐检验员', '无损检测UT Ⅱ级', b'1', '60', 42, 'admin', 1),
('QC-09', '储罐检验员', '受限空间作业证', b'1', '36', 43, 'admin', 1),
('QC-10', '电气防雷防爆检测员', '防雷装置检测资格', b'1', '36', 44, 'admin', 1),
('QC-13', '计量检定员', '计量检定员证', b'1', '60', 45, 'admin', 1),
('SP-01', 'HSE安全员', '注册安全员/安全管理人员证', b'1', '36', 46, 'admin', 1)
ON DUPLICATE KEY UPDATE
  `job_name` = VALUES(`job_name`), `is_required` = VALUES(`is_required`),
  `valid_months` = VALUES(`valid_months`), `sort` = VALUES(`sort`), `updater` = 'admin';

-- 6. 导入学习地图 213 条（按 工种+阶段+课程 幂等）
INSERT INTO `training_learning_map`
  (`job_code`, `job_name`, `stage`, `stage_name`, `stage_no`, `course_code`, `course_name`,
   `is_required`, `sort`, `creator`, `tenant_id`) VALUES
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0101', '公司业务全景与产品线认知', b'1', 1, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0102', '岗位职责与职业行为规范', b'1', 2, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0201', '石油化工现场安全通识', b'1', 3, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0202', '劳动防护用品正确使用', b'1', 4, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0203', '职业健康与粉尘/溶剂危害防护', b'1', 5, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0301', '受限空间作业（储罐/塔罐内部）', b'1', 6, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0302', '高处作业与脚手架安全', b'1', 7, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0303', '动火作业与防火防爆', b'1', 8, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0304', '临时用电与工机具安全', b'1', 9, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0401', '现场应急处置与逃生', b'1', 10, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0402', '典型事故案例复盘', b'1', 11, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0501', '质量管理体系与三检制', b'1', 12, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'A0502', '施工资料与影像留证规范', b'1', 13, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'B0101', '除锈等级判定（Sa/St 标准）', b'1', 14, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'B0201', '防腐涂料体系认知（环氧/聚氨酯/无机锌）', b'1', 15, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'B0203', '环境温湿度露点控制', b'1', 16, 'admin', 1),
('OP-02', '防腐涂装工', 'entry', '入门段(L1→L2)', 1, 'B0302', '刷涂/滚涂与复杂部位处理', b'1', 17, 'admin', 1),
('OP-02', '防腐涂装工', 'core', '核心段(L2→L3)', 2, 'B0102', '喷砂/抛丸设备操作与维护', b'1', 1, 'admin', 1),
('OP-02', '防腐涂装工', 'core', '核心段(L2→L3)', 2, 'B0202', '涂料调配、稀释与适用期控制', b'1', 2, 'admin', 1),
('OP-02', '防腐涂装工', 'core', '核心段(L2→L3)', 2, 'B0301', '高压无气喷涂操作', b'1', 3, 'admin', 1),
('OP-02', '防腐涂装工', 'core', '核心段(L2→L3)', 2, 'B0303', '干膜/湿膜厚度控制与检测', b'1', 4, 'admin', 1),
('OP-02', '防腐涂装工', 'core', '核心段(L2→L3)', 2, 'B0304', '漏点（电火花）检测', b'1', 5, 'admin', 1),
('OP-02', '防腐涂装工', 'core', '核心段(L2→L3)', 2, 'B0503', '地面管线防腐施工', b'1', 6, 'admin', 1),
('OP-02', '防腐涂装工', 'advanced', '进阶段(L3→L4)', 3, 'B0305', '涂层缺陷识别与返修', b'1', 1, 'admin', 1),
('OP-02', '防腐涂装工', 'advanced', '进阶段(L3→L4)', 3, 'B0501', '3PE 三层聚乙烯外防腐工艺', b'1', 2, 'admin', 1),
('OP-02', '防腐涂装工', 'advanced', '进阶段(L3→L4)', 3, 'B0502', '环氧粉末（FBE）熔结防腐', b'1', 3, 'admin', 1),
('OP-02', '防腐涂装工', 'advanced', '进阶段(L3→L4)', 3, 'B0601', '热收缩套/带补口工艺', b'1', 4, 'admin', 1),
('OP-02', '防腐涂装工', 'advanced', '进阶段(L3→L4)', 3, 'B0701', '管道内涂工艺与设备', b'1', 5, 'admin', 1),
('OP-02', '防腐涂装工', 'advanced', '进阶段(L3→L4)', 3, 'C0201', '管道保温施工工艺', b'1', 6, 'admin', 1),
('OP-02', '防腐涂装工', 'advanced', '进阶段(L3→L4)', 3, 'E0101', '无损检测方法总览与选用', b'1', 7, 'admin', 1),
('OP-02', '防腐涂装工', 'technician', '技师段(L4→L5)', 4, 'G0101', '施工方案编制与技术交底', b'1', 1, 'admin', 1),
('OP-02', '防腐涂装工', 'technician', '技师段(L4→L5)', 4, 'G0102', '工艺卡与作业指导书应用', b'1', 2, 'admin', 1),
('OP-02', '防腐涂装工', 'technician', '技师段(L4→L5)', 4, 'G0201', '防腐保温工程验收标准', b'1', 3, 'admin', 1),
('OP-02', '防腐涂装工', 'technician', '技师段(L4→L5)', 4, 'G0301', '班组日常管理与派工', b'1', 4, 'admin', 1),
('OP-02', '防腐涂装工', 'technician', '技师段(L4→L5)', 4, 'H0101', 'MES 系统基础操作', b'1', 5, 'admin', 1),
('OP-02', '防腐涂装工', 'technician', '技师段(L4→L5)', 4, 'H0102', '生产数据与报工录入规范', b'1', 6, 'admin', 1),
('OP-02', '防腐涂装工', 'technician', '技师段(L4→L5)', 4, 'H0201', '报表看板阅读与使用', b'1', 7, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0101', '公司业务全景与产品线认知', b'1', 1, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0102', '岗位职责与职业行为规范', b'1', 2, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0201', '石油化工现场安全通识', b'1', 3, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0202', '劳动防护用品正确使用', b'1', 4, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0203', '职业健康与粉尘/溶剂危害防护', b'1', 5, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0301', '受限空间作业（储罐/塔罐内部）', b'1', 6, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0302', '高处作业与脚手架安全', b'1', 7, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0303', '动火作业与防火防爆', b'1', 8, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0304', '临时用电与工机具安全', b'1', 9, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0401', '现场应急处置与逃生', b'1', 10, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0402', '典型事故案例复盘', b'1', 11, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0501', '质量管理体系与三检制', b'1', 12, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'A0502', '施工资料与影像留证规范', b'1', 13, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'B0101', '除锈等级判定（Sa/St 标准）', b'1', 14, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'B0201', '防腐涂料体系认知（环氧/聚氨酯/无机锌）', b'1', 15, 'admin', 1),
('OP-03', '储罐防腐工', 'entry', '入门段(L1→L2)', 1, 'B0203', '环境温湿度露点控制', b'1', 16, 'admin', 1),
('OP-03', '储罐防腐工', 'core', '核心段(L2→L3)', 2, 'B0102', '喷砂/抛丸设备操作与维护', b'1', 1, 'admin', 1),
('OP-03', '储罐防腐工', 'core', '核心段(L2→L3)', 2, 'B0301', '高压无气喷涂操作', b'1', 2, 'admin', 1),
('OP-03', '储罐防腐工', 'core', '核心段(L2→L3)', 2, 'B0303', '干膜/湿膜厚度控制与检测', b'1', 3, 'admin', 1),
('OP-03', '储罐防腐工', 'core', '核心段(L2→L3)', 2, 'B0304', '漏点（电火花）检测', b'1', 4, 'admin', 1),
('OP-03', '储罐防腐工', 'core', '核心段(L2→L3)', 2, 'B0401', '净化罐/污水罐内防腐工艺', b'1', 5, 'admin', 1),
('OP-03', '储罐防腐工', 'core', '核心段(L2→L3)', 2, 'B0402', '油罐、沉降罐内外防腐', b'1', 6, 'admin', 1),
('OP-03', '储罐防腐工', 'advanced', '进阶段(L3→L4)', 3, 'B0403', '塔罐及球罐防腐', b'1', 1, 'admin', 1),
('OP-03', '储罐防腐工', 'advanced', '进阶段(L3→L4)', 3, 'B0404', '储罐内衬玻璃钢/陶瓷施工', b'1', 2, 'admin', 1),
('OP-03', '储罐防腐工', 'advanced', '进阶段(L3→L4)', 3, 'B0305', '涂层缺陷识别与返修', b'1', 3, 'admin', 1),
('OP-03', '储罐防腐工', 'advanced', '进阶段(L3→L4)', 3, 'B0801', '环氧玻璃钢防腐', b'1', 4, 'admin', 1),
('OP-03', '储罐防腐工', 'advanced', '进阶段(L3→L4)', 3, 'E0302', '储罐检测（含罐底漏磁）', b'1', 5, 'admin', 1),
('OP-03', '储罐防腐工', 'technician', '技师段(L4→L5)', 4, 'G0101', '施工方案编制与技术交底', b'1', 1, 'admin', 1),
('OP-03', '储罐防腐工', 'technician', '技师段(L4→L5)', 4, 'G0201', '防腐保温工程验收标准', b'1', 2, 'admin', 1),
('OP-03', '储罐防腐工', 'technician', '技师段(L4→L5)', 4, 'G0301', '班组日常管理与派工', b'1', 3, 'admin', 1),
('OP-03', '储罐防腐工', 'technician', '技师段(L4→L5)', 4, 'G0202', '不合格品处置与返工管理', b'1', 4, 'admin', 1),
('OP-03', '储罐防腐工', 'technician', '技师段(L4→L5)', 4, 'H0101', 'MES 系统基础操作', b'1', 5, 'admin', 1),
('OP-03', '储罐防腐工', 'technician', '技师段(L4→L5)', 4, 'H0102', '生产数据与报工录入规范', b'1', 6, 'admin', 1),
('OP-03', '储罐防腐工', 'technician', '技师段(L4→L5)', 4, 'H0201', '报表看板阅读与使用', b'1', 7, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0101', '公司业务全景与产品线认知', b'1', 1, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0102', '岗位职责与职业行为规范', b'1', 2, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0201', '石油化工现场安全通识', b'1', 3, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0202', '劳动防护用品正确使用', b'1', 4, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0203', '职业健康与粉尘/溶剂危害防护', b'1', 5, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0301', '受限空间作业（储罐/塔罐内部）', b'1', 6, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0302', '高处作业与脚手架安全', b'1', 7, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0303', '动火作业与防火防爆', b'1', 8, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0304', '临时用电与工机具安全', b'1', 9, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0401', '现场应急处置与逃生', b'1', 10, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0402', '典型事故案例复盘', b'1', 11, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0501', '质量管理体系与三检制', b'1', 12, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'A0502', '施工资料与影像留证规范', b'1', 13, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'C0101', '保温材料认知与选型', b'1', 14, 'admin', 1),
('OP-08', '保温工', 'entry', '入门段(L1→L2)', 1, 'F0103', '保温材料进场检验', b'1', 15, 'admin', 1),
('OP-08', '保温工', 'core', '核心段(L2→L3)', 2, 'C0102', '保温结构层次与厚度计算', b'1', 1, 'admin', 1),
('OP-08', '保温工', 'core', '核心段(L2→L3)', 2, 'C0201', '管道保温施工工艺', b'1', 2, 'admin', 1),
('OP-08', '保温工', 'core', '核心段(L2→L3)', 2, 'C0202', '设备/储罐保温施工', b'1', 3, 'admin', 1),
('OP-08', '保温工', 'core', '核心段(L2→L3)', 2, 'C0203', '铁皮/铝皮保护层制作与安装', b'1', 4, 'admin', 1),
('OP-08', '保温工', 'advanced', '进阶段(L3→L4)', 3, 'C0204', '阀门、法兰等异形件保温', b'1', 1, 'admin', 1),
('OP-08', '保温工', 'advanced', '进阶段(L3→L4)', 3, 'C0403', '保温拆装恢复作业', b'1', 2, 'admin', 1),
('OP-08', '保温工', 'advanced', '进阶段(L3→L4)', 3, 'C0401', '冬防保温方案与实施', b'1', 3, 'admin', 1),
('OP-08', '保温工', 'advanced', '进阶段(L3→L4)', 3, 'C0402', '电伴热与蒸汽伴热配合', b'1', 4, 'admin', 1),
('OP-08', '保温工', 'advanced', '进阶段(L3→L4)', 3, 'B0101', '除锈等级判定（Sa/St 标准）', b'1', 5, 'admin', 1),
('OP-08', '保温工', 'technician', '技师段(L4→L5)', 4, 'G0101', '施工方案编制与技术交底', b'1', 1, 'admin', 1),
('OP-08', '保温工', 'technician', '技师段(L4→L5)', 4, 'G0201', '防腐保温工程验收标准', b'1', 2, 'admin', 1),
('OP-08', '保温工', 'technician', '技师段(L4→L5)', 4, 'G0301', '班组日常管理与派工', b'1', 3, 'admin', 1),
('OP-08', '保温工', 'technician', '技师段(L4→L5)', 4, 'H0101', 'MES 系统基础操作', b'1', 4, 'admin', 1),
('OP-08', '保温工', 'technician', '技师段(L4→L5)', 4, 'H0102', '生产数据与报工录入规范', b'1', 5, 'admin', 1),
('OP-08', '保温工', 'technician', '技师段(L4→L5)', 4, 'H0201', '报表看板阅读与使用', b'1', 6, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0101', '公司业务全景与产品线认知', b'1', 1, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0102', '岗位职责与职业行为规范', b'1', 2, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0201', '石油化工现场安全通识', b'1', 3, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0202', '劳动防护用品正确使用', b'1', 4, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0203', '职业健康与粉尘/溶剂危害防护', b'1', 5, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0301', '受限空间作业（储罐/塔罐内部）', b'1', 6, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0302', '高处作业与脚手架安全', b'1', 7, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0303', '动火作业与防火防爆', b'1', 8, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0304', '临时用电与工机具安全', b'1', 9, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0401', '现场应急处置与逃生', b'1', 10, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0402', '典型事故案例复盘', b'1', 11, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0501', '质量管理体系与三检制', b'1', 12, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'A0502', '施工资料与影像留证规范', b'1', 13, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'C0101', '保温材料认知与选型', b'1', 14, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'entry', '入门段(L1→L2)', 1, 'C0301', '保冷工艺与材料（聚氨酯/泡沫玻璃）', b'1', 15, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'core', '核心段(L2→L3)', 2, 'C0102', '保温结构层次与厚度计算', b'1', 1, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'core', '核心段(L2→L3)', 2, 'C0302', '防潮层与隔汽层施工', b'1', 2, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'core', '核心段(L2→L3)', 2, 'C0201', '管道保温施工工艺', b'1', 3, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'core', '核心段(L2→L3)', 2, 'C0203', '铁皮/铝皮保护层制作与安装', b'1', 4, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'advanced', '进阶段(L3→L4)', 3, 'C0303', '低温装置保冷质量控制', b'1', 1, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'advanced', '进阶段(L3→L4)', 3, 'C0204', '阀门、法兰等异形件保温', b'1', 2, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'advanced', '进阶段(L3→L4)', 3, 'C0403', '保温拆装恢复作业', b'1', 3, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'advanced', '进阶段(L3→L4)', 3, 'F0103', '保温材料进场检验', b'1', 4, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'technician', '技师段(L4→L5)', 4, 'G0101', '施工方案编制与技术交底', b'1', 1, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'technician', '技师段(L4→L5)', 4, 'G0201', '防腐保温工程验收标准', b'1', 2, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'technician', '技师段(L4→L5)', 4, 'G0301', '班组日常管理与派工', b'1', 3, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'technician', '技师段(L4→L5)', 4, 'H0101', 'MES 系统基础操作', b'1', 4, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'technician', '技师段(L4→L5)', 4, 'H0102', '生产数据与报工录入规范', b'1', 5, 'admin', 1),
('OP-09', '保冷工（绝热施工）', 'technician', '技师段(L4→L5)', 4, 'H0201', '报表看板阅读与使用', b'1', 6, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0101', '公司业务全景与产品线认知', b'1', 1, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0102', '岗位职责与职业行为规范', b'1', 2, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0201', '石油化工现场安全通识', b'1', 3, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0202', '劳动防护用品正确使用', b'1', 4, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0203', '职业健康与粉尘/溶剂危害防护', b'1', 5, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0301', '受限空间作业（储罐/塔罐内部）', b'1', 6, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0302', '高处作业与脚手架安全', b'1', 7, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0303', '动火作业与防火防爆', b'1', 8, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0304', '临时用电与工机具安全', b'1', 9, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0401', '现场应急处置与逃生', b'1', 10, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0402', '典型事故案例复盘', b'1', 11, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0501', '质量管理体系与三检制', b'1', 12, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'A0502', '施工资料与影像留证规范', b'1', 13, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'D0101', '管道内衬修复工艺总览', b'1', 14, 'admin', 1),
('OP-12', '内衬施工工', 'entry', '入门段(L1→L2)', 1, 'D0104', '内衬管选型与采购要点', b'1', 15, 'admin', 1),
('OP-12', '内衬施工工', 'core', '核心段(L2→L3)', 2, 'D0102', '高分子聚乙烯内衬施工', b'1', 1, 'admin', 1),
('OP-12', '内衬施工工', 'core', '核心段(L2→L3)', 2, 'D0103', '非金属油管内衬', b'1', 2, 'admin', 1),
('OP-12', '内衬施工工', 'core', '核心段(L2→L3)', 2, 'D0301', '管道缺陷评估与修复选型', b'1', 3, 'admin', 1),
('OP-12', '内衬施工工', 'advanced', '进阶段(L3→L4)', 3, 'B0404', '储罐内衬玻璃钢/陶瓷施工', b'1', 1, 'admin', 1),
('OP-12', '内衬施工工', 'advanced', '进阶段(L3→L4)', 3, 'D0302', '补强套筒/复合材料补强', b'1', 2, 'admin', 1),
('OP-12', '内衬施工工', 'advanced', '进阶段(L3→L4)', 3, 'D0303', '带压堵漏技术', b'1', 3, 'admin', 1),
('OP-12', '内衬施工工', 'advanced', '进阶段(L3→L4)', 3, 'E0201', '废油管检测与分级', b'1', 4, 'admin', 1),
('OP-12', '内衬施工工', 'technician', '技师段(L4→L5)', 4, 'G0101', '施工方案编制与技术交底', b'1', 1, 'admin', 1),
('OP-12', '内衬施工工', 'technician', '技师段(L4→L5)', 4, 'G0201', '防腐保温工程验收标准', b'1', 2, 'admin', 1),
('OP-12', '内衬施工工', 'technician', '技师段(L4→L5)', 4, 'G0301', '班组日常管理与派工', b'1', 3, 'admin', 1),
('OP-12', '内衬施工工', 'technician', '技师段(L4→L5)', 4, 'H0101', 'MES 系统基础操作', b'1', 4, 'admin', 1),
('OP-12', '内衬施工工', 'technician', '技师段(L4→L5)', 4, 'H0102', '生产数据与报工录入规范', b'1', 5, 'admin', 1),
('OP-12', '内衬施工工', 'technician', '技师段(L4→L5)', 4, 'H0201', '报表看板阅读与使用', b'1', 6, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0101', '公司业务全景与产品线认知', b'1', 1, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0102', '岗位职责与职业行为规范', b'1', 2, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0201', '石油化工现场安全通识', b'1', 3, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0202', '劳动防护用品正确使用', b'1', 4, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0203', '职业健康与粉尘/溶剂危害防护', b'1', 5, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0301', '受限空间作业（储罐/塔罐内部）', b'1', 6, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0302', '高处作业与脚手架安全', b'1', 7, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0303', '动火作业与防火防爆', b'1', 8, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0304', '临时用电与工机具安全', b'1', 9, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0401', '现场应急处置与逃生', b'1', 10, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0402', '典型事故案例复盘', b'1', 11, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0501', '质量管理体系与三检制', b'1', 12, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'A0502', '施工资料与影像留证规范', b'1', 13, 'admin', 1),
('QC-03', '超声检测员（UT）', 'entry', '入门段(L1→L2)', 1, 'E0101', '无损检测方法总览与选用', b'1', 14, 'admin', 1),
('QC-03', '超声检测员（UT）', 'core', '核心段(L2→L3)', 2, 'E0102', '超声检测（UT）基础与实操', b'1', 1, 'admin', 1),
('QC-03', '超声检测员（UT）', 'core', '核心段(L2→L3)', 2, 'E0503', '检测报告编制与数据判定', b'1', 2, 'admin', 1),
('QC-03', '超声检测员（UT）', 'advanced', '进阶段(L3→L4)', 3, 'E0201', '废油管检测与分级', b'1', 1, 'admin', 1),
('QC-03', '超声检测员（UT）', 'advanced', '进阶段(L3→L4)', 3, 'E0202', '钻杆、钻具油套管检测', b'1', 2, 'admin', 1),
('QC-03', '超声检测员（UT）', 'advanced', '进阶段(L3→L4)', 3, 'E0204', '压力管道检测', b'1', 3, 'admin', 1),
('QC-03', '超声检测员（UT）', 'advanced', '进阶段(L3→L4)', 3, 'E0205', '工业管道与长输油管检测', b'1', 4, 'admin', 1),
('QC-03', '超声检测员（UT）', 'advanced', '进阶段(L3→L4)', 3, 'E0106', '涡流/漏磁检测', b'1', 5, 'admin', 1),
('QC-03', '超声检测员（UT）', 'technician', '技师段(L4→L5)', 4, 'E0301', '压力容器检测', b'1', 1, 'admin', 1),
('QC-03', '超声检测员（UT）', 'technician', '技师段(L4→L5)', 4, 'E0302', '储罐检测（含罐底漏磁）', b'1', 2, 'admin', 1),
('QC-03', '超声检测员（UT）', 'technician', '技师段(L4→L5)', 4, 'G0201', '防腐保温工程验收标准', b'1', 3, 'admin', 1),
('QC-03', '超声检测员（UT）', 'technician', '技师段(L4→L5)', 4, 'G0202', '不合格品处置与返工管理', b'1', 4, 'admin', 1),
('QC-03', '超声检测员（UT）', 'technician', '技师段(L4→L5)', 4, 'H0101', 'MES 系统基础操作', b'1', 5, 'admin', 1),
('QC-03', '超声检测员（UT）', 'technician', '技师段(L4→L5)', 4, 'H0201', '报表看板阅读与使用', b'1', 6, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0101', '公司业务全景与产品线认知', b'1', 1, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0102', '岗位职责与职业行为规范', b'1', 2, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0201', '石油化工现场安全通识', b'1', 3, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0202', '劳动防护用品正确使用', b'1', 4, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0203', '职业健康与粉尘/溶剂危害防护', b'1', 5, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0301', '受限空间作业（储罐/塔罐内部）', b'1', 6, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0302', '高处作业与脚手架安全', b'1', 7, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0303', '动火作业与防火防爆', b'1', 8, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0304', '临时用电与工机具安全', b'1', 9, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0401', '现场应急处置与逃生', b'1', 10, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0402', '典型事故案例复盘', b'1', 11, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0501', '质量管理体系与三检制', b'1', 12, 'admin', 1),
('MG-01', '班组长', 'entry', '入门段(L1→L2)', 1, 'A0502', '施工资料与影像留证规范', b'1', 13, 'admin', 1),
('MG-01', '班组长', 'core', '核心段(L2→L3)', 2, 'G0301', '班组日常管理与派工', b'1', 1, 'admin', 1),
('MG-01', '班组长', 'core', '核心段(L2→L3)', 2, 'G0102', '工艺卡与作业指导书应用', b'1', 2, 'admin', 1),
('MG-01', '班组长', 'core', '核心段(L2→L3)', 2, 'H0101', 'MES 系统基础操作', b'1', 3, 'admin', 1),
('MG-01', '班组长', 'core', '核心段(L2→L3)', 2, 'H0102', '生产数据与报工录入规范', b'1', 4, 'admin', 1),
('MG-01', '班组长', 'advanced', '进阶段(L3→L4)', 3, 'G0101', '施工方案编制与技术交底', b'1', 1, 'admin', 1),
('MG-01', '班组长', 'advanced', '进阶段(L3→L4)', 3, 'G0201', '防腐保温工程验收标准', b'1', 2, 'admin', 1),
('MG-01', '班组长', 'advanced', '进阶段(L3→L4)', 3, 'G0202', '不合格品处置与返工管理', b'1', 3, 'admin', 1),
('MG-01', '班组长', 'advanced', '进阶段(L3→L4)', 3, 'G0302', '工程量确认与结算基础', b'1', 4, 'admin', 1),
('MG-01', '班组长', 'advanced', '进阶段(L3→L4)', 3, 'H0103', '施工影像与资料上传规范', b'1', 5, 'admin', 1),
('MG-01', '班组长', 'advanced', '进阶段(L3→L4)', 3, 'H0201', '报表看板阅读与使用', b'1', 6, 'admin', 1),
('MG-01', '班组长', 'technician', '技师段(L4→L5)', 4, 'G0303', '产能建设管线项目要点', b'1', 1, 'admin', 1),
('MG-01', '班组长', 'technician', '技师段(L4→L5)', 4, 'G0304', '天然气处理厂工程要点', b'1', 2, 'admin', 1),
('MG-01', '班组长', 'technician', '技师段(L4→L5)', 4, 'G0305', '维修工程组织与管理', b'1', 3, 'admin', 1),
('MG-01', '班组长', 'technician', '技师段(L4→L5)', 4, 'F0201', '防腐材料采购流程与供方管理', b'1', 4, 'admin', 1),
('MG-01', '班组长', 'technician', '技师段(L4→L5)', 4, 'H0202', '招投标信息识别与响应基础', b'1', 5, 'admin', 1)
ON DUPLICATE KEY UPDATE
  `job_name` = VALUES(`job_name`), `stage_name` = VALUES(`stage_name`), `stage_no` = VALUES(`stage_no`),
  `course_name` = VALUES(`course_name`), `is_required` = VALUES(`is_required`),
  `sort` = VALUES(`sort`), `updater` = 'admin';

-- 7. 注册后台菜单
-- 归属依据《赵升茹-数据字典与一级子模块关联.docx》「主支撑模块（强）」列，
-- 沿用本项目既有数据字典的落位规则（取主支撑模块中的首个强支撑模块，不新增子模块）：
--   课程分类 → 模块二 课程管理(41003)「分类管理」
--   成长等级 → 模块二 课程管理(41003)「成长等级维护」
--   晋级门槛 → 模块九 数据统计与档案归档(41010)「晋级门槛管理」
-- 本次三张字典表同规则落位：
--   岗位工种   → 模块二 课程管理(41003)：2-1 课程「适用岗位工种」标记取自工种表
--   工种资格证 → 模块三 培训管理(41004)：3-5「上岗资格核查」判断持证/过期
--   学习地图   → 模块三 培训管理(41004)：3-1 培训班「绑定课程清单」课程路径编排
-- 语句幂等：已存在则纠正归属，兼容早期误挂在 41000 下的「基础数据」目录版本。
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
VALUES
  (41151, '岗位工种', 'training:job:query', 2, 70, 41003, 'job', 'lucide:hard-hat', 'training/basedata/job/index', 'TrainingBaseDataJob', 0, 'admin')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `permission` = VALUES(`permission`), `type` = VALUES(`type`), `sort` = VALUES(`sort`),
  `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
  `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
  `status` = VALUES(`status`), `deleted` = b'0', `updater` = 'admin';
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
VALUES
  (41152, '工种资格证', 'training:job-cert:query', 2, 60, 41004, 'job-cert', 'lucide:badge-check', 'training/basedata/job-cert/index', 'TrainingBaseDataJobCert', 0, 'admin')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `permission` = VALUES(`permission`), `type` = VALUES(`type`), `sort` = VALUES(`sort`),
  `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
  `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
  `status` = VALUES(`status`), `deleted` = b'0', `updater` = 'admin';
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
VALUES
  (41153, '学习地图', 'training:learning-map:query', 2, 70, 41004, 'learning-map', 'lucide:map', 'training/basedata/learning-map/index', 'TrainingBaseDataLearningMap', 0, 'admin')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `permission` = VALUES(`permission`), `type` = VALUES(`type`), `sort` = VALUES(`sort`),
  `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`), `icon` = VALUES(`icon`),
  `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
  `status` = VALUES(`status`), `deleted` = b'0', `updater` = 'admin';
-- 清理早期版本误建的「基础数据」目录(41150)，避免云培训学院下多出一个子模块
DELETE FROM `system_role_menu` WHERE `menu_id` = 41150;
DELETE FROM `system_menu` WHERE `id` = 41150;
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
SELECT 41154, '岗位工种新增', 'training:job:create', 3, 1, 41151, '', '', NULL, NULL, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 41154 AND `deleted` = b'0');
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
SELECT 41155, '岗位工种编辑', 'training:job:update', 3, 2, 41151, '', '', NULL, NULL, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 41155 AND `deleted` = b'0');
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
SELECT 41156, '岗位工种删除', 'training:job:delete', 3, 3, 41151, '', '', NULL, NULL, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 41156 AND `deleted` = b'0');
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
SELECT 41157, '资格证新增', 'training:job-cert:create', 3, 1, 41152, '', '', NULL, NULL, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 41157 AND `deleted` = b'0');
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
SELECT 41158, '资格证编辑', 'training:job-cert:update', 3, 2, 41152, '', '', NULL, NULL, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 41158 AND `deleted` = b'0');
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
SELECT 41159, '资格证删除', 'training:job-cert:delete', 3, 3, 41152, '', '', NULL, NULL, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 41159 AND `deleted` = b'0');
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
SELECT 41160, '学习地图新增', 'training:learning-map:create', 3, 1, 41153, '', '', NULL, NULL, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 41160 AND `deleted` = b'0');
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
SELECT 41161, '学习地图编辑', 'training:learning-map:update', 3, 2, 41153, '', '', NULL, NULL, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 41161 AND `deleted` = b'0');
INSERT INTO `system_menu`
  (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `creator`)
SELECT 41162, '学习地图删除', 'training:learning-map:delete', 3, 3, 41153, '', '', NULL, NULL, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 41162 AND `deleted` = b'0');

-- =====================================================================
-- 关联一致性自检（联调测试用，只读）
-- =====================================================================
-- 岗位工种学习域编码是否全部命中课程分类：
-- SELECT j.code, j.learn_domain, j.learn_domain_codes FROM training_job j WHERE j.deleted = b'0';
-- 学习地图课程编码是否全部命中课程分类 L3：
-- SELECT m.course_code FROM training_learning_map m
--   LEFT JOIN training_course_category c ON c.code = m.course_code AND c.level = 3 AND c.deleted = b'0'
--   WHERE m.deleted = b'0' AND c.id IS NULL;
-- 资格证/学习地图是否存在悬空工种：
-- SELECT DISTINCT c.job_code FROM training_job_cert c LEFT JOIN training_job j ON j.code = c.job_code
--   WHERE j.id IS NULL;
-- =====================================================================
