-- =====================================================================
-- MES 安全环保检测（SET）模块 - 应急管理五表 DDL（二期 §八）
-- 表：mes_set_emergency_plan   应急预案（备案 / 3 年评估修订）
--     mes_set_emergency_material 应急物资台账（定点存放 + 临期预警）
--     mes_set_emergency_drill  应急演练（计划 → 记录 → 评估 → 整改闭环）
--     mes_set_emergency_event  应急事件（上报 → 处置 → 待报告 → 闭环）
--     mes_set_emergency_waste  应急废物明细（逐桶：过秤记录 + 危废台账行）
-- 依据：《MES环保模块质优化设计-团队终版-2026-09-09.md》§八、§10.1；
--       《危险品全生命周期管理-优化设计文档-2026-09-09.md》§10
-- 命名偏离（同 §17.1）：设计稿写的是 mes_env_emergency_* / mes_dg_emergency_*，
--       仓库里 SET 前缀统一是 mes_set_*，故落为 mes_set_emergency_*。
-- 说明：
--   1. 公共 6 列：creator/create_time/updater/update_time/deleted/tenant_id
--   2. 枚举用 VARCHAR 代码存储（与 SET 其余表一致），中文只进 COMMENT
--   3. **建表只给普通索引，业务编号不给 UNIQUE**：本模块删除是逻辑删除（deleted 0/1），
--      唯一键只含业务列时"建-删-再建同一编号"必然撞键 500（见 drop-code-unique-20260910.sql
--      抬头的长注释），查重一律交回服务层 *_NO_DUPLICATE。
--   4. emergency_waste 是 event 的子表，**没有独立 CRUD 接口**：只由事件处置事务逐桶写入，
--      桶码 ↔ 称重记录 ↔ 危废台账行三者在这里对齐，供追溯反查"危废桶从哪来"。
-- 执行：mysql -h127.0.0.1 -uroot -proot --default-character-set=utf8mb4 ruoyi-vue-pro < emergency-20260910-ddl.sql
-- =====================================================================

-- 1) 应急预案
CREATE TABLE IF NOT EXISTS `mes_set_emergency_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `plan_no` varchar(64) NOT NULL COMMENT '预案编号',
  `plan_name` varchar(128) NOT NULL COMMENT '预案名称',
  `plan_type` varchar(32) NOT NULL COMMENT '预案类型：COMPREHENSIVE(综合)/SPECIAL(专项)/ONSITE(现场处置)',
  `version` varchar(32) DEFAULT NULL COMMENT '版本号',
  `publish_date` date DEFAULT NULL COMMENT '发布日期（备案时限与评估周期的起算点）',
  `filing_deadline` date DEFAULT NULL COMMENT '备案截止日（发布 + 20 个工作日，派生值不进表单）',
  `filing_no` varchar(64) DEFAULT NULL COMMENT '备案号（报生态环境部门后登记）',
  `filing_date` date DEFAULT NULL COMMENT '备案日期',
  `attach_url` varchar(255) DEFAULT NULL COMMENT '预案附件地址',
  `last_review_date` date DEFAULT NULL COMMENT '上次评估修订日期',
  `next_review_date` date DEFAULT NULL COMMENT '下次评估修订日期（发布或上次修订 + 3 年，派生值不进表单）',
  `review_reason` varchar(255) DEFAULT NULL COMMENT '修订原因（工艺/物料/法规变化）',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT(草稿)/PUBLISHED(已发布)/FILED(已备案)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_plan_no` (`plan_no`),
  KEY `idx_status` (`status`),
  KEY `idx_filing_deadline` (`filing_deadline`),
  KEY `idx_next_review` (`next_review_date`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-应急预案';

-- 2) 应急物资台账
CREATE TABLE IF NOT EXISTS `mes_set_emergency_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `material_no` varchar(64) NOT NULL COMMENT '物资编号',
  `material_name` varchar(128) NOT NULL COMMENT '物资名称',
  `material_type` varchar(32) NOT NULL COMMENT '物资类型：DRY_SAND(干沙)/OIL_ABSORBENT(吸油毡)/CHEM_SUIT(防化服)/GAS_MASK(防毒面具)/EXPLOSION_TOOL(防爆工具)/SANDBAG(围堰沙袋)/EYE_WASH(洗眼器)/DRY_POWDER(干粉灭火器)',
  `spec` varchar(64) DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(16) DEFAULT NULL COMMENT '计量单位',
  `quantity` decimal(12,3) DEFAULT NULL COMMENT '在库数量',
  `storage_location` varchar(128) DEFAULT NULL COMMENT '定点存放位置',
  `produce_date` date DEFAULT NULL COMMENT '生产日期',
  `expire_date` date DEFAULT NULL COMMENT '有效期至（临期预警依据）',
  `last_check_date` date DEFAULT NULL COMMENT '最近检查日期',
  `status` varchar(32) DEFAULT NULL COMMENT '状态：NORMAL(正常)/EXPIRING(临期)/EXPIRED(过期)/OUT(缺货)，派生值不进表单',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_material_no` (`material_no`),
  KEY `idx_material_type` (`material_type`),
  KEY `idx_expire_date` (`expire_date`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-应急物资';

-- 3) 应急演练
CREATE TABLE IF NOT EXISTS `mes_set_emergency_drill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `drill_no` varchar(64) NOT NULL COMMENT '演练编号',
  `drill_name` varchar(128) NOT NULL COMMENT '演练名称',
  `plan_id` bigint DEFAULT NULL COMMENT '关联预案编号（须为已发布/已备案的预案）',
  `plan_version` varchar(32) DEFAULT NULL COMMENT '演练时的预案版本快照',
  `drill_type` varchar(32) NOT NULL COMMENT '演练类型：COMPREHENSIVE(综合)/SPECIAL(专项)/ONSITE(现场处置)',
  `drill_date` date NOT NULL COMMENT '演练日期',
  `participant_count` int DEFAULT NULL COMMENT '参加人数',
  `participants` varchar(500) DEFAULT NULL COMMENT '参加人员',
  `sign_sheet_url` varchar(255) DEFAULT NULL COMMENT '签到表附件',
  `photo_url` varchar(255) DEFAULT NULL COMMENT '演练照片',
  `video_url` varchar(255) DEFAULT NULL COMMENT '演练视频',
  `evaluation` varchar(500) DEFAULT NULL COMMENT '演练评估',
  `rectify_requirement` varchar(500) DEFAULT NULL COMMENT '整改要求（填写后整改状态转 PENDING）',
  `rectify_status` varchar(32) DEFAULT NULL COMMENT '整改状态：NONE(无需整改)/PENDING(待整改)/DONE(已整改)',
  `rectify_done_date` date DEFAULT NULL COMMENT '整改完成日期',
  `closed_date` date DEFAULT NULL COMMENT '闭环日期',
  `status` varchar(32) NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED(已计划)/DONE(已演练)/CLOSED(已闭环)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_drill_no` (`drill_no`),
  KEY `idx_drill_date` (`drill_date`),
  KEY `idx_plan_id` (`plan_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-应急演练';

-- 4) 应急事件
CREATE TABLE IF NOT EXISTS `mes_set_emergency_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `event_no` varchar(64) NOT NULL COMMENT '事件编号',
  `event_type` varchar(32) NOT NULL COMMENT '事件类型：LEAK(泄漏)/EXCEED(超标)/FACILITY_FAULT(设施故障)/OTHER(其他)',
  `scenario` varchar(32) DEFAULT NULL COMMENT '泄漏场景（对应处置卡）：MDI_LEAK/THINNER_LEAK/WASTE_OIL_LEAK',
  `occur_time` datetime DEFAULT NULL COMMENT '发生时间',
  `location` varchar(128) DEFAULT NULL COMMENT '发生地点',
  `chemical_code` varchar(64) DEFAULT NULL COMMENT '涉事化学品编码（关联危化品档案取 MSDS/禁配）',
  `leak_quantity` decimal(12,3) DEFAULT NULL COMMENT '泄漏量',
  `impact_scope` varchar(255) DEFAULT NULL COMMENT '影响范围',
  `report_user` varchar(64) DEFAULT NULL COMMENT '上报人',
  `report_time` datetime DEFAULT NULL COMMENT '上报时间（PDA 一键上报）',
  `handler` varchar(64) DEFAULT NULL COMMENT '处置人（派发时指定）',
  `dispatch_time` datetime DEFAULT NULL COMMENT '派发时间',
  `dispose_note` varchar(500) DEFAULT NULL COMMENT '处置说明',
  `dispose_photo_url` varchar(255) DEFAULT NULL COMMENT '处置照片',
  `waste_count` int DEFAULT NULL COMMENT '应急废物桶数（处置时按明细回填，非人工填写）',
  `waste_quantity` decimal(12,3) DEFAULT NULL COMMENT '应急废物合计净重 kg（处置时按明细回填，非人工填写）',
  `report_content` varchar(1000) DEFAULT NULL COMMENT '事件报告（原因/数量/处置/整改）',
  `approver` varchar(64) DEFAULT NULL COMMENT '负责人',
  `approve_time` datetime DEFAULT NULL COMMENT '签字时间',
  `closed_time` datetime DEFAULT NULL COMMENT '闭环时间',
  `status` varchar(32) NOT NULL DEFAULT 'REPORTED' COMMENT '状态：REPORTED(已上报)/DISPOSING(处置中)/PENDING_REPORT(待报告)/CLOSED(已闭环)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_event_no` (`event_no`),
  KEY `idx_status` (`status`),
  KEY `idx_occur_time` (`occur_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-应急事件';

-- 5) 应急废物明细（事件子表，只由处置事务写入）
CREATE TABLE IF NOT EXISTS `mes_set_emergency_waste` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `event_id` bigint NOT NULL COMMENT '应急事件编号',
  `event_no` varchar(64) NOT NULL COMMENT '应急事件编号（冗余，便于按单号直查）',
  `container_code` varchar(64) NOT NULL COMMENT '危废桶码（贴 HJ1276 标签的那只桶）',
  `waste_code` varchar(32) NOT NULL COMMENT '危废类别代码（泄漏吸附物按场景落 HW49/HW06/HW08）',
  `waste_name` varchar(128) DEFAULT NULL COMMENT '危废名称',
  `net_weight` decimal(12,3) NOT NULL COMMENT '净重 kg（过秤所得，不是手填）',
  `storage_location` varchar(128) DEFAULT NULL COMMENT '入库暂存库位',
  `weigh_record_id` bigint DEFAULT NULL COMMENT '称重记录编号（mes_set_weigh_record，过秤证据）',
  `hazwaste_id` bigint DEFAULT NULL COMMENT '危废台账行编号（mes_set_hazardous_waste）',
  `manifest_no` varchar(64) DEFAULT NULL COMMENT '危废台账业务号（冗余，便于反查）',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_event_id` (`event_id`),
  KEY `idx_event_no` (`event_no`),
  KEY `idx_container` (`container_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='安全环保检测-应急废物明细';
